package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume.BingXVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.bingx.exchangeinfo.BingXSymbolData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiBingX implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "BingX";

    public final static String BASE_ENDPOINT = "https://open-api.bingx.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final String TYPE_REQUEST = "step0";

    private final WebClient webClient;

    public ApiBingX() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/openApi/spot/v1/common/symbols";

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от BingX, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от BingX, код: " + statusCode);
        }

        try {
            BingXSymbolData data = objectMapper.readValue(responseEntity.getBody(), BingXSymbolData.class);
            return data.getData().getSymbols().stream()
                    .filter(symbol -> symbol.getSymbol().endsWith("-USDT") && symbol.getStatus() == 1)
                    .map(symbol -> {
                        String coinName = CoinFactory.refactorToStandardCoinName(symbol.getSymbol(), "-");
                        return CoinFactory.getCoin(coinName);
                    })
                    .collect(Collectors.toSet());
        } catch (IOException ex) {
            log.error("Ошибка десериализации ответа от BingX", ex);
            throw new RuntimeException("Ошибка десериализации ответа от BingX", ex);
        }
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

        coins.forEach(coin -> {
            BingXVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getData().getFirst().getQuoteVolume()));
                coinsWithVolume24h.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithVolume24h;
    }

    private Mono<BingXVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "-USDT";

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/openApi/spot/v1/ticker/24hr")
                        .queryParam("symbol", symbol)
                        .queryParam("timestamp", new Timestamp(System.currentTimeMillis()).getTime())
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
                .bodyToMono(BingXVolumeTicker.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }

    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin + "_USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/openApi/spot/v2/market/depth")
                                .queryParam("symbol", coin)
                                .queryParam("depth", DEPTH_REQUEST_LIMIT)
                                .queryParam("type", TYPE_REQUEST)
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
                        .bodyToFlux(String.class)
                        .map(response -> {
                            try {
                                BingXCoinDepth bingXCoinDepth = objectMapper.readValue(response, BingXCoinDepth.class);
                                bingXCoinDepth.setCoinName(coin.replaceAll("_USDT", ""));

                                return ApiExchangeUtils.getBingXCoinDepth(bingXCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }
}
