package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.mexc.chains.MexcChainResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.depth.MexcCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee.MexcTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.coins.MexcCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.tickervolume.MexcCoinTicker;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.CoinChainUtils;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.Mexc.MexcCoinDepthBuilder;
import com.exchange.scanner.services.utils.Mexc.MexcSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiMEXC implements ApiExchange {

    @Value("${exchanges.apiKeys.MEXC.key}")
    private String key;

    @Value("${exchanges.apiKeys.MEXC.secret}")
    private String secret;

    private static final String NAME = "MEXC";

    public final static String BASE_ENDPOINT = "https://api.mexc.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiMEXC() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        MexcCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getSymbols() == null) return coins;

        coins = response.getSymbols().stream()
                .filter(symbol -> symbol.getStatus().equals("ENABLED") &&
                        symbol.getQuoteAsset().equals("USDT") &&
                        symbol.getIsSpotTradingAllowed()
                )
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + symbol.getBaseAsset().toUpperCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + symbol.getBaseAsset().toUpperCase());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseAsset().toUpperCase() + "_USDT");
                    return ObjectUtils.getCoin(symbol.getBaseAsset(), NAME, links, symbol.getIsMarginTradingAllowed());
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<MexcCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/exchangeInfo")
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
            .bodyToMono(MexcCurrencyResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<MexcChainResponse> response = getChainResponse().collectList().block();

        if (response == null || response.getFirst() == null) {
            log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            return chainsDTOSet;
        }
        Set<MexcChainResponse> filteredData = response.stream()
                .filter(data -> coinsNames.contains(data.getCoin()))
                .filter(data -> data.getNetworkList().stream()
                        .allMatch(network -> network.getDepositEnable() && network.getWithdrawEnable())
                )
                .collect(Collectors.toSet());

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            filteredData.forEach(data -> {
                if (coin.getName().equals(data.getCoin())) {
                    data.getNetworkList()
                        .forEach(networkList -> {
                            String chainName = CoinChainUtils.unifyChainName(networkList.getNetWork());
                            Chain chain = new Chain();
                            chain.setName(chainName);
                            chain.setCommission(new BigDecimal(networkList.getWithdrawFee()));
                            chain.setMinConfirm(networkList.getMinConfirm());
                            chains.add(chain);
                        });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Flux<MexcChainResponse> getChainResponse() {
        Map<String, String> params = new HashMap<>();
        String signature = MexcSignatureBuilder.generateMexcSignature(params, secret);
        params.put("signature", signature);

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path("/api/v3/capital/config/getall");
                params.forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .header("X-MEXC-APIKEY", key)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(MexcChainResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            MexcTradingFeeResponse response = getFee(coin).block();

            if (response != null && response.getData() != null) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        response.getData().getTakerCommission()
                );
                tradingFeeSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return tradingFeeSet;
    }

    private Mono<MexcTradingFeeResponse> getFee(Coin coin) {
        Map<String, String> params = new HashMap<>();
        params.put("symbol", coin.getName() + "USDT");
        String signature = MexcSignatureBuilder.generateMexcSignature(params, secret);
        params.put("signature", signature);
        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path("api/v3/tradeFee");
                params.forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .header("X-MEXC-APIKEY", key)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(MexcTradingFeeResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            MexcCoinTicker response = getCoinTickerVolume(coin).block();

            if (response != null && response.getQuoteVolume() != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getQuoteVolume()
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

    private Mono<MexcCoinTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/ticker/24hr")
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
            .bodyToMono(MexcCoinTicker.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            MexcCoinDepth response = getCoinDepth(coin).block();

            if (response != null && (response.getAsks() != null || response.getBids() != null)) {
                CoinDepth coinDepth = MexcCoinDepthBuilder.getCoinDepth(coin, response, exchange);
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

    private Mono<MexcCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/depth")
                    .queryParam("symbol", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
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
            .bodyToMono(MexcCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}