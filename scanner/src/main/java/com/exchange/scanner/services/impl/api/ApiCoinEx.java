package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinex.tickervolume.CoinExVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.coinex.exchangeinfo.CoinExSymbolData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.ListUtils;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiCoinEx implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "CoinEx";

    public final static String BASE_ENDPOINT = "https://api.coinex.com/v2";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 25;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final int REQUEST_INTERVAL = 0;

    private final WebClient webClient;

    public ApiCoinEx() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/market";

        ResponseEntity<CoinExSymbolData> responseEntity = restTemplate.getForEntity(url, CoinExSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от CoinEx, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от CoinEx, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getQuoteCcy().equals("USDT"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCcy()))
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

        CoinExVolumeTicker response = getCoinTicker(new ArrayList<>(coins)).blockLast();

        if (response == null) return coinsWithVolume24h;
        coins.forEach(coin -> {
            response.getData().forEach(responseData -> {
                if (coin.getName().equals(responseData.getMarket().replaceAll("USDT", ""))) {
                    coin.setVolume24h(new BigDecimal(responseData.getValue()));
                    coinsWithVolume24h.add(coin);
                }
            });
        });

        return coinsWithVolume24h;
    }

    private Flux<CoinExVolumeTicker> getCoinTicker(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);

        return Flux.fromIterable(partitions)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(partition -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/spot/ticker")
                                .queryParam("market", generateParameters(partition))
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
                        .bodyToFlux(CoinExVolumeTicker.class));
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }

    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin + "USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/spot/depth")
                                .queryParam("market", coin)
                                .queryParam("limit", DEPTH_REQUEST_LIMIT)
                                .queryParam("interval", REQUEST_INTERVAL)
                                .build())
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                    log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                                    return Mono.empty();
                                })
                        )
                        .bodyToFlux(String.class)
                        .map(response -> {
                            try {
                                CoinExCoinDepth coinExCoinDepth = objectMapper.readValue(response, CoinExCoinDepth.class);

                                return ApiExchangeUtils.getCoinExCoinDepth(coinExCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters = "";
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> {
            sb.append(coin.getSymbol()).append("USDT").append(",");
        });
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
