package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.okx.depth.OKXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.okx.exchangeinfo.OKXSymbolData;
import com.exchange.scanner.dto.response.exchangedata.okx.ticker.OKXDataTicker;
import com.exchange.scanner.dto.response.exchangedata.okx.ticker.OKXTicker;
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

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiOKX implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "OKX";

    public final static String BASE_ENDPOINT = "https://www.okx.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiOKX() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/api/v5/public/instruments?instType=SPOT";

        ResponseEntity<OKXSymbolData> responseEntity = restTemplate.getForEntity(url, OKXSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от OKX, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от OKX, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getState().equals("live"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCcy()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<OKXDataTicker> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiOKX::getCoinDataTickerDTO)
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
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v5/market/books")
                                    .queryParam("instId", coin)
                                    .queryParam("sz", DEPTH_REQUEST_LIMIT)
                                    .build())
                        .retrieve()
                        .bodyToFlux(String.class)
                        .onErrorResume(throwable -> {
                            log.error("Ошибка получения информации от " + NAME + ". Причина: {}", throwable.getLocalizedMessage());
                            return Flux.empty();
                        })
                        .map(response -> {
                            try {
                                OKXCoinDepth okxCoinDepth = objectMapper.readValue(response, OKXCoinDepth.class);
                                okxCoinDepth.setCoinName(coin.replaceAll("-USDT", ""));

                                return ApiExchangeUtils.getOKXCoinDepth(okxCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<List<OKXDataTicker>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "-USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v5/market/tickers")
                        .queryParam("instType", "SPOT")
                        .build()
                )
                .retrieve()
                .bodyToFlux(OKXTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(OKXTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getInstId()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(OKXDataTicker ticker) {
        return new CoinDataTicker(
                ticker.getInstId().replaceAll("-", ""),
                ticker.getVol24h(),
                ticker.getBidPx(),
                ticker.getAskPx()
        );
    }

    private static boolean isNotEmptyValues(OKXDataTicker ticker) {
        return ticker.getBidPx() != null &&
                ticker.getAskPx() != null &&
                ticker.getVol24h() != null &&
                !ticker.getBidPx().isEmpty() &&
                !ticker.getAskPx().isEmpty() &&
                !ticker.getVol24h().isEmpty();
    }
}
