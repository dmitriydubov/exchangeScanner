package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.huobi.chains.HuobiChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.huobi.coins.HuobiCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume.HuobiVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiFeeData;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.AppUtils.ListUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import com.exchange.scanner.services.utils.Huobi.HuobiCoinDepthBuilder;
import com.huobi.client.TradeClient;
import com.huobi.client.req.trade.FeeRateRequest;
import com.huobi.constant.HuobiOptions;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiHuobi implements ApiExchange {

    @Value("${exchanges.apiKeys.Huobi.key}")
    private String key;

    @Value("${exchanges.apiKeys.Huobi.secret}")
    private String secret;

    private static final String NAME = "Huobi";

    public final static String BASE_ENDPOINT = "https://api.huobi.pro";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final String AGGREGATION_LEVEL_TYPE = "step0";

    private final WebClient webClient;

    public ApiHuobi() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        HuobiCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQcdn().equals("USDT") && symbol.getTe())
                .map(symbol -> ObjectUtils.getCoin(symbol.getBcdn()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<HuobiCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/settings/common/symbols")
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка валют. Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(HuobiCurrencyResponse.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();

        coins.forEach(coin -> {
            HuobiChainsResponse response = getChains(coin).block();

            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.getData().getFirst().getChains().forEach(chainResponse -> {
                    Chain chain = new Chain();
                    chain.setName(chainResponse.getDisplayName());
                    chain.setCommission(new BigDecimal(chainResponse.getTransactFeeWithdraw()));
                    chains.add(chain);
                });
                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return chainsDTOSet;
    }

    private Mono<HuobiChainsResponse> getChains(Coin coin) {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/reference/currencies")
                    .queryParam("currency", coin.getName().toLowerCase())
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(HuobiChainsResponse.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        HuobiTradingFeeResponse response = getFee(coins);

        Set<String> symbols = coins.stream()
                .map(coin -> coin.getName().toLowerCase() + "usdt")
                .collect(Collectors.toSet());
        List<HuobiFeeData> data = response.getData().stream()
                .filter(dataResponse -> symbols.contains(dataResponse.getSymbol()))
                .toList();
        coins.forEach(coin -> {
            data.forEach(feeResponse -> {
                if (coin.getName().equalsIgnoreCase(feeResponse.getSymbol().replaceAll("usdt", ""))) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            feeResponse.getTakerFeeRate()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private HuobiTradingFeeResponse getFee(Set<Coin> coins) {
        HuobiTradingFeeResponse response = new HuobiTradingFeeResponse();
        List<HuobiFeeData> data = new ArrayList<>();
        int requestSymbolMaxSize = 20;
        List<List<Coin>> partitions = ListUtils.partition(new ArrayList<>(coins), requestSymbolMaxSize);
        TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                .apiKey(key)
                .secretKey(secret)
                .build());
        partitions.forEach(partition -> {
            FeeRateRequest request = new FeeRateRequest(generateSymbolsParameters(partition));
            tradeService.getFeeRate(request).forEach(feeRate -> {
                HuobiFeeData huobiFeeData = new HuobiFeeData();
                huobiFeeData.setSymbol(feeRate.getSymbol());
                huobiFeeData.setTakerFeeRate(String.valueOf(feeRate.getTakerFeeRate()));
                data.add(huobiFeeData);
            });
        });
        response.setData(data);
        return response;
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            HuobiVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getTick().getVol()
                );

                volume24HSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return volume24HSet;
    }

    private Mono<HuobiVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName().toLowerCase() + "usdt";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/market/detail/merged")
                    .queryParam("symbol", symbol)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торгового объёма за 24 часа от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(HuobiVolumeTicker.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            HuobiCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = HuobiCoinDepthBuilder.getCoinDepth(coin, response.getTick());
                coinDepthSet.add(coinDepth);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepthSet;
    }

    private Mono<HuobiCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName.toLowerCase() + "usdt";

        return  webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/market/depth")
                    .queryParam("symbol", symbol)
                    .queryParam("type", AGGREGATION_LEVEL_TYPE)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(HuobiCoinDepth.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
    }

    private static String generateSymbolsParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName().toLowerCase()).append("usdt").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
