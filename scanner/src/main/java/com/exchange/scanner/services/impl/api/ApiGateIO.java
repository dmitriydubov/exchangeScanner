package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.gateio.depth.GateIOCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.gateio.exchangeinfo.GateIOSymbolData;
import com.exchange.scanner.dto.response.exchangedata.gateio.ticker.GateIOCoinTicker;
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
public class ApiGateIO implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Gate.io";

    private final static String BASE_ENDPOINT = "https://api.gateio.ws/api/v4";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiGateIO() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/currencies";

        ResponseEntity<GateIOSymbolData[]> responseEntity = restTemplate.getForEntity(url, GateIOSymbolData[].class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Gate.io, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Gate.io, код: " + statusCode);
        }

        return Arrays.stream(responseEntity.getBody())
                .filter(symbol -> !symbol.getDeListed() &&
                        !symbol.getTradeDisabled() &&
                        !symbol.getWithdrawDisabled() &&
                        !symbol.getDepositDisabled()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getCurrency()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<CoinDataTicker> response = getCoinTicker(new ArrayList<>(coins)).map(ApiGateIO::getCoinDataTickerDTO);
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
        List<String> coinSymbols = coins.stream().map(coin -> coin + "_USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/spot/order_book")
                                .queryParam("currency_pair", coin)
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
                                GateIOCoinDepth gateIOCoinDepth = objectMapper.readValue(response, GateIOCoinDepth.class);
                                gateIOCoinDepth.setCoinName(coin.replaceAll("_USDT", ""));

                                return ApiExchangeUtils.getGateIOCoinDepth(gateIOCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<GateIOCoinTicker> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream().map(coin -> coin.getSymbol() + "_USDT").toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/spot/tickers")
                        .build()
                )
                .retrieve()
                .bodyToFlux(GateIOCoinTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .filter(ticker -> coinsSymbols.contains(ticker.getCurrencyPair()) && isNotEmptyValues(ticker));
    }

    private static CoinDataTicker getCoinDataTickerDTO(GateIOCoinTicker ticker) {
        return new CoinDataTicker(
                ticker.getCurrencyPair().replaceAll("_", ""),
                ticker.getQuoteVolume(),
                ticker.getHighestBid(),
                ticker.getLowestAsk()
        );
    }

    private static boolean isNotEmptyValues(GateIOCoinTicker ticker) {
        return ticker.getHighestBid() != null &&
                ticker.getLowestAsk() != null &&
                ticker.getQuoteVolume() != null &&
                !ticker.getHighestBid().isEmpty() &&
                !ticker.getLowestAsk().isEmpty() &&
                !ticker.getQuoteVolume().isEmpty();
    }
}
