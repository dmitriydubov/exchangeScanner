package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.poloniex.exchangeinfo.PoloniexSymbolData;
import com.exchange.scanner.dto.response.exchangedata.poloniex.exchangeinfo.Symbols;
import com.exchange.scanner.dto.response.exchangedata.poloniex.ticker.PoloniexTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
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

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiPoloniex implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Poloniex";

    public final static String BASE_ENDPOINT = "https://api.poloniex.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiPoloniex() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/currencies";

        ResponseEntity<PoloniexSymbolData[]> responseEntity = restTemplate.getForEntity(url, PoloniexSymbolData[].class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Poloniex, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Poloniex, код: " + statusCode);
        }

        return Arrays.stream(responseEntity.getBody()).filter(data -> {
            Symbols symbolSettings = data.getCurrencies().values().stream().reduce((symbols, symbols2) -> symbols).orElseThrow(() -> new RuntimeException("Ошибка получения данных с Poloniex"));
            return !symbolSettings.getDeListed() &&
                    symbolSettings.getTradingState().equals("NORMAL") &&
                    symbolSettings.getWalletDepositState().equals("ENABLED") &&
                    symbolSettings.getWalletWithdrawalState().equals("ENABLED");
        }).map(data -> {
            String coinName = data.getCurrencies().keySet().stream().reduce((key1, key2) -> key1).get();
            Coin coin = new Coin();
            coin.setName(coinName);
            coin.setSymbol(coinName);
            return coin;
        }).collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<PoloniexTicker> response = getCoinTicker(new ArrayList<>(coins));

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiPoloniex::getCoinDataTickerDTO)
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
        List<String> coinSymbols = coins.stream().map(coin -> coin + "_USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> {
                             URI uri = uriBuilder.path("markets/{symbol}/orderBook")
                                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
                                    .build(coin);
                             System.out.println("URI " + BASE_ENDPOINT + uri.getPath());
                             return uri;
                        })
                        .retrieve()
                        .bodyToFlux(String.class)
                        .onErrorResume(throwable -> {
                            log.error("Ошибка получения информации от " + NAME + ". Причина: {}", throwable.getLocalizedMessage());
                            return Flux.empty();
                        })
                        .map(response -> {
                            System.out.println(response);
                            try {
                                PoloniexCoinDepth poloniexCoinDepth = objectMapper.readValue(response, PoloniexCoinDepth.class);
                                poloniexCoinDepth.setCoinName(coin.replaceAll("_USDT", ""));

                                return ApiExchangeUtils.getPoloniexCoinDepth(poloniexCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<PoloniexTicker> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "_USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("markets/ticker24h")
                        .build()
                )
                .retrieve()
                .bodyToFlux(PoloniexTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker));
    }

    private static CoinDataTicker getCoinDataTickerDTO(PoloniexTicker ticker) {
        return new CoinDataTicker(
                ticker.getSymbol().replaceAll("_", ""),
                ticker.getAmount(),
                ticker.getBid(),
                ticker.getAsk()
        );
    }

    private static boolean isNotEmptyValues(PoloniexTicker ticker) {
        return ticker.getBid() != null &&
                ticker.getAsk() != null &&
                ticker.getAmount() != null &&
                !ticker.getBid().isEmpty() &&
                !ticker.getAsk().isEmpty() &&
                !ticker.getAmount().isEmpty();
    }
}
