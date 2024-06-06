package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.*;
import com.exchange.scanner.dto.response.exchangedata.gateio.exchangeinfo.GateIOSymbolData;
import com.exchange.scanner.dto.response.exchangedata.gateio.ticker.GateIOCoinTicker;
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
public class ApiGateIO implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "Gate.io";

    private final static String BASE_ENDPOINT = "https://api.gateio.ws/api/v4";

    private static final int TIMEOUT = 10000;

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
