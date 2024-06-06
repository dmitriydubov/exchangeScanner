package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.lbank.exchangeinfo.LBankSymbolData;
import com.exchange.scanner.dto.response.exchangedata.lbank.ticker.LBankTicker;
import com.exchange.scanner.dto.response.exchangedata.lbank.ticker.LBankTickerData;
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
public class ApiLBank implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "LBank";

    public final static String BASE_ENDPOINT = "https://api.lbkex.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiLBank() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/v2/accuracy.do";

        ResponseEntity<LBankSymbolData> responseEntity = restTemplate.getForEntity(url, LBankSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от LBank, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от LBank, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .map(symbol -> {
                    String coinName = CoinFactory.refactorToStandardCoinName(symbol.getSymbol(), "_");
                    return CoinFactory.getCoin(coinName);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<LBankTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiLBank::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    private Flux<List<LBankTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol().toLowerCase() + "_usdt")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/ticker/24hr.do")
                        .queryParam("symbol", "all")
                        .build()
                )
                .retrieve()
                .bodyToFlux(LBankTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(LBankTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static boolean isNotEmptyValues(LBankTickerData ticker) {
        return ticker.getTicker().getLatest() != null &&
                ticker.getTicker().getVol() != null &&
                !ticker.getTicker().getLatest().isEmpty() &&
                !ticker.getTicker().getVol().isEmpty();
    }

    private static CoinDataTicker getCoinDataTickerDTO(LBankTickerData ticker) {
        return new CoinDataTicker(
                ticker.getSymbol().toUpperCase().replaceAll("_", ""),
                ticker.getTicker().getVol(),
                ticker.getTicker().getLatest(),
                ticker.getTicker().getLatest()
        );
    }
}
