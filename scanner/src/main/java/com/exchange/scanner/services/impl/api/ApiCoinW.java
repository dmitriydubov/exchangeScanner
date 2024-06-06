package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.coinw.exchangeinfo.CoinWSymbolData;
import com.exchange.scanner.dto.response.exchangedata.coinw.ticker.CoinWTicker;
import com.exchange.scanner.dto.response.exchangedata.coinw.ticker.CoinWTickerData;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.impl.api.utils.CoinFactory;
import com.exchange.scanner.services.impl.api.utils.WebClientBuilder;
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
public class ApiCoinW implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "CoinW";

    public final static String BASE_ENDPOINT = "https://www.coinw.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiCoinW() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/appApi.html?action=currencys";

        ResponseEntity<CoinWSymbolData> responseEntity = restTemplate.getForEntity(url, CoinWSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от CoinW, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от CoinW, код: " + statusCode);
        }

        return responseEntity.getBody().getData().getSymbols().values().stream()
                .filter(symbol -> !symbol.getRecharge().equals("0") && !symbol.getWithDraw().equals("0"))
                .map(symbol -> CoinFactory.getCoin(symbol.getShortName()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<CoinWTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiCoinW::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    private Flux<List<CoinWTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(Coin::getSymbol)
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/appApi.html")
                        .queryParam("action", "symbols")
                        .build()
                )
                .retrieve()
                .bodyToFlux(CoinWTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от" + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от" + NAME, throwable);
                })
                .flatMapIterable(CoinWTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getQuoteCurrency()) &&
                        ticker.getFStatus() == 1 &&
                        isNotEmptyValues(ticker)
                )
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(CoinWTickerData ticker) {
        return new CoinDataTicker(
                ticker.getQuoteCurrency() + ticker.getBaseCurrency(),
                ticker.getTotal24(),
                ticker.getLatestDealPrice(),
                ticker.getLatestDealPrice()
        );
    }

    private static boolean isNotEmptyValues(CoinWTickerData ticker) {
        return ticker.getLatestDealPrice() != null &&
                ticker.getTotal24() != null &&
                !ticker.getLatestDealPrice().isEmpty() &&
                !ticker.getTotal24().isEmpty();
    }
}
