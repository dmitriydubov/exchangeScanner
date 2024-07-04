package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinw.exchangeinfo.CoinWSymbolData;
import com.exchange.scanner.dto.response.exchangedata.coinw.symbol.CoinWSymbol;
import com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume.CoinWVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume.CoinWVolumeTickerData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;;

@Service
@Slf4j
public class ApiCoinW implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "CoinW";

    public final static String BASE_ENDPOINT = "https://www.coinw.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private final WebClient webClient;

    public ApiCoinW() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/appApi.html?action=symbols";

        ResponseEntity<CoinWSymbolData> responseEntity = restTemplate.getForEntity(url, CoinWSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от CoinW, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от CoinW, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getBaseCurrency().equals("USDT") && symbol.getFStatus() == 1)
                .map(symbol -> CoinFactory.getCoin(symbol.getQuoteCurrency()))
                .collect(Collectors.toSet());
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

        CoinWVolumeTicker response = getCoinTickerVolume().block();

        if (response == null) return coinsWithVolume24h;

        List<CoinWVolumeTickerData> dataList = response.getData().stream()
                .filter(responseData -> responseData.getBaseCurrency().equals("USDT"))
                .toList();

        coins.forEach(coin -> {
            dataList.forEach(responseData -> {
                if (coin.getName().equals(responseData.getQuoteCurrency())) {
                    coin.setVolume24h(new BigDecimal(responseData.getTotal24()));
                    coinsWithVolume24h.add(coin);
                }
            });
        });

        return coinsWithVolume24h;
    }

    private Mono<CoinWVolumeTicker> getCoinTickerVolume() {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/appApi.html")
                        .queryParam("action", "symbols")
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
                .bodyToMono(CoinWVolumeTicker.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Map<String, String> coinsNumbers = getCoinNumber(coins);
        Flux<CoinDepth> response = getCoinDepth(coinsNumbers);

        return new HashSet<>(Objects.requireNonNull(response.collectList().block()));
    }

    private Map<String, String> getCoinNumber(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin + "/USDT").toList();
        Flux<Map<String, String>> coinNumbers = Flux.fromIterable(coinSymbols)
            .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
            .flatMap(coin -> webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/appApi.html")
                        .queryParam("action", "getSymbol")
                        .queryParam("symbol", coin)
                        .build()
                )
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения идентификатора монеты от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToFlux(CoinWSymbol.class)
                .map(response -> Collections.singletonMap(
                        coin, ApiExchangeUtils.getCoinWSymbolNumber(response.getData().getSymbol(), coin))
                )
            );

        return coinNumbers.blockFirst();
    }


    private Flux<CoinDepth> getCoinDepth(Map<String, String> coinNumbers) {
        Map.Entry<String, String> entry = coinNumbers.entrySet().iterator().next();
        String coin = entry.getKey();
        String coinNumber = entry.getValue();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/appApi.html")
                        .queryParam("action", "depth")
                        .queryParam("symbol", coinNumber)
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
                .bodyToFlux(CoinWCoinDepth.class)
                .map(response -> {
                    response.setCoinName(coin.replaceAll("/USDT", ""));
                    return ApiExchangeUtils.getCoinWCoinDepth(response);
                });
    }
}
