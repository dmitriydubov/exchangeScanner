package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.exchangeinfo.BybitSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bybit.ticker.BybitCoinTicker;
import com.exchange.scanner.dto.response.exchangedata.bybit.ticker.BybitCoinTickerList;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
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
public class ApiBybit implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Bybit";

    public static final String BASE_ENDPOINT = "https://api.bybit.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBybit() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/v3/public/symbols";

        ResponseEntity<BybitSymbolData> responseEntity = restTemplate.getForEntity(url, BybitSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bybit, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bybit, код: " + statusCode);
        }

        return responseEntity.getBody()
                .getResult().getList()
                .stream().filter(symbol -> symbol.getShowStatus().equals("1"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCoin()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<BybitCoinTickerList> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiBybit::getCoinDataTickerDTO)
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
        List<String> coinSymbols = coins.stream().map(coin -> coin + "USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/spot/v3/public/quote/depth")
                                .queryParam("symbol", coin)
                                .queryParam("limit", DEPTH_REQUEST_LIMIT)
                                .build()
                        )
                        .retrieve()
                        .bodyToFlux(String.class)
                        .onErrorResume(throwable -> {
                            log.error("Ошибка получения информации от " + NAME + ". Причина: {}", throwable.getLocalizedMessage());
                            return Flux.empty();
                        })
                        .map(response -> {
                            try {
                                BybitCoinDepth bybitCoinDepth = objectMapper.readValue(response, BybitCoinDepth.class);
                                bybitCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getBybitCoinDepth(bybitCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<List<BybitCoinTickerList>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/spot/v3/public/quote/ticker/24hr")
                        .build()
                )
                .retrieve()
                .bodyToFlux(BybitCoinTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(ticker -> ticker.getResult().getList())
                .filter(result -> coinsSymbols.contains(result.getS()) && isNotEmptyValues(result))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(BybitCoinTickerList tickerList) {
        return new CoinDataTicker(
                tickerList.getS(),
                tickerList.getV(),
                tickerList.getBp(),
                tickerList.getAp()
        );
    }

    private static boolean isNotEmptyValues(BybitCoinTickerList result) {
        return result.getBp() != null &&
                result.getAp() != null &&
                result.getV() != null &&
                !result.getBp().isEmpty() &&
                !result.getAp().isEmpty() &&
                !result.getV().isEmpty();
    }
}
