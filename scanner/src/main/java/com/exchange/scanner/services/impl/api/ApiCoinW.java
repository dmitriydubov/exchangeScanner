package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinw.chains.CoinWChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.coins.CoinWCoinAvailableResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWCoinDepthResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume.CoinWVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.tradingfee.CoinWTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.AppUtils.CoinFactory;
import com.exchange.scanner.services.utils.CoinW.CoinWCoinDepthBuilder;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
;

@Service
@Slf4j
public class ApiCoinW implements ApiExchange {

    private static final String NAME = "CoinW";

    public final static String BASE_ENDPOINT = "https://www.coinw.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiCoinW() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        CoinWCoinAvailableResponse response = getCurrencies().block();
        if (response == null) return coins;
        coins = response.getData().stream()
                .filter(currency -> currency.getCurrencyQuote().equals("USDT") && currency.getState() == 1)
                .map(currency -> CoinFactory.getCoin(currency.getCurrencyBase()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<CoinWCoinAvailableResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnSymbol")
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
            .bodyToMono(CoinWCoinAvailableResponse.class);
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        CoinWChainsResponse response = getChains().block();
        if (response == null)return coinsWithChains;

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            response.getData().forEach((symbol, chainResponse) -> {
                if (coin.getName().equals(symbol) && chainResponse.getRecharge().equals("1") && chainResponse.getWithDraw().equals("1")) {
                    Chain chain = new Chain();
                    chain.setName(chainResponse.getChain());
                    chain.setCommission(new BigDecimal(chainResponse.getTxFee()));
                    chains.add(chain);
                }
            });
            coin.setChains(chains);
            coinsWithChains.add(coin);
        });

        return coinsWithChains;
    }

    private Mono<CoinWChainsResponse> getChains() {

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnCurrencies")
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
            .bodyToMono(CoinWChainsResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithTradingFee = new HashSet<>();
        CoinWTradingFeeResponse response = getFee().block();
        if (response == null) return coinsWithTradingFee;
        coins.forEach(coin -> {
            response.getData().forEach((key, value) -> {
                if (coin.getName().equals(key)) {
                    coin.setTakerFee(new BigDecimal(value.getTxFee()));
                    coinsWithTradingFee.add(coin);
                }
            });
        });

        return coinsWithTradingFee;
    }

    private Mono<CoinWTradingFeeResponse> getFee() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/appApi.html")
                    .queryParam("action", "currencys")
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinWTradingFeeResponse.class);
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        CoinWVolumeResponse response = getCoinTickerVolume().block();

        if (response == null) return coinsWithVolume24h;

        coins.forEach(coin -> {
           response.getData().forEach((coinName, data) -> {
               if (coin.getName().equals(coinName.replaceAll("_USDT", ""))) {
                   coin.setVolume24h(new BigDecimal(data.getBaseVolume()));
                   coinsWithVolume24h.add(coin);
               }
           });
        });

        return coinsWithVolume24h;
    }

    private Mono<CoinWVolumeResponse> getCoinTickerVolume() {

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnTicker")
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
            .bodyToMono(CoinWVolumeResponse.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet= new HashSet<>();

        coins.forEach(coin -> {
            CoinWCoinDepthResponse response = getDepthResponse(coin).block();
            if (response != null) {
                CoinDepth coinDepth = CoinWCoinDepthBuilder.getCoinDepth(coin, response.getData());
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

    private Mono<CoinWCoinDepthResponse> getDepthResponse(String coin) {
        String symbol = coin + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnOrderBook")
                    .queryParam("symbol", symbol)
                    .queryParam("size", DEPTH_REQUEST_LIMIT)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения стакана цен для символа {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinWCoinDepthResponse.class);
    }
}
