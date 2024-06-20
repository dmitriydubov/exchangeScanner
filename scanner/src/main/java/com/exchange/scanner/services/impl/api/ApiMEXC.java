package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.mexc.depth.MexcCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.mexc.exchangeinfo.MexcSymbolData;
import com.exchange.scanner.dto.response.exchangedata.mexc.ticker.MexcCoinTicker;
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
public class ApiMEXC implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "MEXC";

    public final static String BASE_ENDPOINT = "https://api.mexc.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiMEXC() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/api/v3/exchangeInfo";

        ResponseEntity<MexcSymbolData> responseEntity = restTemplate.getForEntity(url, MexcSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от MEXC, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от MEXC, код: " + statusCode);
        }

        return responseEntity.getBody().getSymbols().stream()
                .filter(symbol -> symbol.getStatus().equals("ENABLED"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseAsset()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<CoinDataTicker> response = getCoinTicker(new ArrayList<>(coins))
                .map(ApiMEXC::getCoinDataTickerDTO);

        List<CoinDataTicker> coinDataTickers = response.collectList().block();

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
                        .uri(uriBuilder -> uriBuilder.path("/api/v3/depth")
                                .queryParam("symbol", coin)
                                .queryParam("limit", DEPTH_REQUEST_LIMIT)
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
                                MexcCoinDepth mexcCoinDepth = objectMapper.readValue(response, MexcCoinDepth.class);
                                mexcCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getMexcCoinDepth(mexcCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<MexcCoinTicker> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/ticker/24hr")
                        .build()
                )
                .retrieve()
                .bodyToFlux(MexcCoinTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker));
    }

    private static CoinDataTicker getCoinDataTickerDTO(MexcCoinTicker ticker) {
        return new CoinDataTicker(
                ticker.getSymbol(),
                ticker.getVolume(),
                ticker.getBidPrice(),
                ticker.getAskPrice()
        );
    }

    private static boolean isNotEmptyValues(MexcCoinTicker ticker) {
        return ticker.getBidPrice() != null &&
                ticker.getAskPrice() != null &&
                ticker.getVolume() != null &&
                !ticker.getBidPrice().isEmpty() &&
                !ticker.getAskPrice().isEmpty() &&
                !ticker.getVolume().isEmpty();
    }
}
