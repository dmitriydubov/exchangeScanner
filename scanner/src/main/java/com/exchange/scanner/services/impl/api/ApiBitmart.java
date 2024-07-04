package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bitmart.depth.BitmartCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitmart.exchangeinfo.BitmartSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bitmart.tickervolume.BitmartVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
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
public class ApiBitmart implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Bitmart";

    public final static String BASE_ENDPOINT = "https://api-cloud.bitmart.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiBitmart() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/v1/symbols/details";

        ResponseEntity<BitmartSymbolData> responseEntity = restTemplate.getForEntity(url, BitmartSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bitmart, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bitmart, код: " + statusCode);
        }

        return responseEntity.getBody().getData().getSymbols().stream()
                .filter(symbol -> symbol.getQuoteCurrency().equals("USDT") && symbol.getTradeStatus().equals("trading"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCurrency()))
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

        coins.forEach(coin -> {
            BitmartVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getData().getQv24h()));
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

    public Mono<BitmartVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getSymbol() + "_USDT";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/spot/quotation/v3/ticker")
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
                .bodyToMono(BitmartVolumeTicker.class);
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
                        .uri(uriBuilder -> uriBuilder.path("/spot/quotation/v3/books")
                                .queryParam("symbol", coin)
                                .queryParam("limit", DEPTH_REQUEST_LIMIT)
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
                                BitmartCoinDepth bitmartCoinDepth = objectMapper.readValue(response, BitmartCoinDepth.class);

                                return ApiExchangeUtils.getBitmartCoinDepth(bitmartCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }
}
