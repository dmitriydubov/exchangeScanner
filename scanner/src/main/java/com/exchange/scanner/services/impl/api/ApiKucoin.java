package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.kucoin.exchangeinfo.KucoinSymbolData;
import com.exchange.scanner.dto.response.exchangedata.kucoin.ticker.KucoinTicker;
import com.exchange.scanner.dto.response.exchangedata.kucoin.ticker.KucoinTickerCoinData;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiKucoin implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Kucoin";

    public final static String BASE_ENDPOINT = "https://api.kucoin.com";

    private static final int TIMEOUT = 10000;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiKucoin() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/api/v3/currencies";

        ResponseEntity<KucoinSymbolData> responseEntity = restTemplate.getForEntity(url, KucoinSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Kucoin, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Kucoin, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .map(symbol -> CoinFactory.getCoin(symbol.getCurrency()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<KucoinTicker> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiKucoin::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }


    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin + "-USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/market/orderbook/level2_" + DEPTH_REQUEST_LIMIT)
                                .queryParam("symbol", coin)
                                .build()
                        )
                        .retrieve()
                        .bodyToFlux(String.class)
                        .onErrorMap(throwable -> {
                            log.error("Ошибка получения информации от " + NAME, throwable);
                            return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                        })
                        .map(response -> {
                            try {
                                KucoinCoinDepth kucoinCoinDepth = objectMapper.readValue(response, KucoinCoinDepth.class);
                                kucoinCoinDepth.setCoinName(coin.replaceAll("-USDT", ""));

                                return ApiExchangeUtils.getKucoinCoinDepth(kucoinCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<List<KucoinTicker>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "-USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/market/allTickers")
                        .build()
                )
                .retrieve()
                .bodyToFlux(KucoinTickerCoinData.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(data -> data.getData().getTicker())
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(KucoinTicker ticker) {
        return new CoinDataTicker(
                ticker.getSymbol().replaceAll("-", ""),
                ticker.getVol(),
                ticker.getBuy(),
                ticker.getSell()
        );
    }

    private static boolean isNotEmptyValues(KucoinTicker ticker) {
        return ticker.getBuy() != null &&
                ticker.getSell() != null &&
                ticker.getVol() != null &&
                !ticker.getBuy().isEmpty() &&
                !ticker.getSell().isEmpty() &&
                !ticker.getVol().isEmpty();
    }
}
