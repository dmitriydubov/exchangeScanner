package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.lbank.depth.LBankCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.lbank.exchangeinfo.LBankSymbolData;
import com.exchange.scanner.dto.response.exchangedata.lbank.ticker.LBankTicker;
import com.exchange.scanner.dto.response.exchangedata.lbank.ticker.LBankTickerData;
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
public class ApiLBank implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "LBank";

    public final static String BASE_ENDPOINT = "https://api.lbkex.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

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

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }


    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin.toLowerCase() + "_usdt").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/v2/depth.do")
                                .queryParam("symbol", coin)
                                .queryParam("size", DEPTH_REQUEST_LIMIT)
                                .build())
                        .retrieve()
                        .bodyToFlux(String.class)
                        .onErrorResume(throwable -> {
                            log.error("Ошибка получения информации от " + NAME + ". Причина: {}", throwable.getLocalizedMessage());
                            return Flux.empty();
                        })
                        .map(response -> {
                            try {
                                LBankCoinDepth lBankCoinDepth = objectMapper.readValue(response, LBankCoinDepth.class);
                                lBankCoinDepth.setCoinName(coin.replaceAll("_usdt", "").toUpperCase());

                                return ApiExchangeUtils.getLBankCoinDepth(lBankCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
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
