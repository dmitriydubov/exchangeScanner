package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.huobi.chains.HuobiChainsData;
import com.exchange.scanner.dto.response.exchangedata.huobi.chains.HuobiChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.huobi.coins.HuobiCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume.HuobiVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiFeeData;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Huobi.HuobiCoinDepthBuilder;
import com.huobi.client.TradeClient;
import com.huobi.client.req.trade.FeeRateRequest;
import com.huobi.constant.HuobiOptions;
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
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        HuobiCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQcdn().equals("USDT") && symbol.getTe())
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + symbol.getBcdn().toLowerCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + symbol.getBcdn().toLowerCase());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBcdn().toLowerCase() + "_usdt" + "?type=spot");
                    return ObjectUtils.getCoin(symbol.getBcdn(), NAME, links, false);
                })
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
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        HuobiChainsResponse response = getChains().block();
        if (response == null || response.getData() == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<HuobiChainsData> chainsData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCurrency().toUpperCase()))
                .filter(data -> data.getChains().stream()
                        .allMatch(chain -> chain.getDepositStatus().equals("allowed") &&
                                chain.getWithdrawStatus().equals("allowed"))
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainsData.forEach(data -> {
                if (data.getCurrency().equalsIgnoreCase(coin.getName())) {
                    data.getChains().forEach(chainResponse -> {
                        String chainName = CoinChainUtils.unifyChainName(chainResponse.getDisplayName());
                        Chain chain = new Chain();
                        chain.setName(chainName.toUpperCase());
                        if (chainResponse.getTransactFeeWithdraw() == null) {
                            chain.setCommission(new BigDecimal(chainResponse.getTransactFeeRateWithdraw()));
                        } else {
                            chain.setCommission(new BigDecimal(chainResponse.getTransactFeeWithdraw()));
                        }
                        chain.setMinConfirm(chainResponse.getNumOfConfirmations());
                        chains.add(chain);
                    });
                }
            });

            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<HuobiChainsResponse> getChains() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/reference/currencies")
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
                LogsUtils.createErrorResumeLogs(error, NAME);
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
        coins.forEach(coin -> data.forEach(feeResponse -> {
            if (coin.getName().equalsIgnoreCase(feeResponse.getSymbol().replaceAll("usdt", ""))) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        feeResponse.getTakerFeeRate()
                );
                tradingFeeSet.add(responseDTO);
            }
        }));

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

            if (response != null && response.getTick() != null) {
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
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            HuobiCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getTick() != null) {
                CoinDepth coinDepth = HuobiCoinDepthBuilder.getCoinDepth(coin, response.getTick(), exchange);
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

    private Mono<HuobiCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName().toLowerCase() + "usdt";

        return  webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/market/depth")
                    .queryParam("symbol", symbol)
                    .queryParam("depth", DEPTH_REQUEST_LIMIT)
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
                LogsUtils.createErrorResumeLogs(error, NAME);
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
