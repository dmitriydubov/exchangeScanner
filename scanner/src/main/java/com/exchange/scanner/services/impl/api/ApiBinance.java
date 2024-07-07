package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.binance.tickervolume.BinanceCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.binance.coins.BinanceCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.Binance.BinanceCoinDepthBuilder;
import com.exchange.scanner.services.utils.AppUtils.CoinFactory;
import com.exchange.scanner.services.utils.AppUtils.ListUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBinance implements ApiExchange {

    private static final String NAME = "Binance";

    private static final String BASE_ENDPOINT = "https://api.binance.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBinance() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        BinanceCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getSymbols().stream()
                .filter(symbol -> symbol.getQuoteAsset().equals("USDT") &&
                        symbol.getStatus().equals("TRADING") &&
                        symbol.getIsSpotTradingAllowed()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseAsset()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BinanceCurrencyResponse> getCurrencies() {
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
            .bodyToMono(BinanceCurrencyResponse.class);
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        return Set.of();
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        return Set.of();
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        List<BinanceCoinTickerVolume> response = getCoinTickerVolume(new ArrayList<>(coins)).collectList().block();

        if (response == null) return coinsWithVolume24h;

        coins.forEach(coin -> {
            response.forEach(tradingFeeResponse -> {
                if (coin.getName().equals(tradingFeeResponse.getSymbol().replaceAll("USDT", ""))) {
                    coin.setVolume24h(new BigDecimal(tradingFeeResponse.getQuoteVolume()));
                    coinsWithVolume24h.add(coin);
                }
            });
        });

        return coinsWithVolume24h;
    }

    private Flux<BinanceCoinTickerVolume> getCoinTickerVolume(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);
        return Flux.fromIterable(partitions)
            .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
            .flatMap(partition -> webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v3/ticker/24hr")
                        .queryParam("symbols", generateParameters(partition))
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
                .bodyToFlux(BinanceCoinTickerVolume.class)
            );
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            BinanceCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = BinanceCoinDepthBuilder.getCoinDepth(coin, response);
                coinDepthSet.add(coinDepth);
            }
        });

        return coinDepthSet;
    }

    private Mono<BinanceCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "USDT";

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
            .bodyToMono(BinanceCoinDepth.class);
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        coins.forEach(coin -> sb.append("\"").append(coin.getName()).append("USDT").append("\"").append(","));
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        parameters = sb.toString();

        return parameters;
    }
}
