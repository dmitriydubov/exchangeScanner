package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bitmart.exchangeinfo.BitmartSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bitmart.ticker.BitmartTicker;
import com.exchange.scanner.dto.response.exchangedata.bitmart.ticker.BitmartTickerData;
import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.impl.api.utils.CoinFactory;
import com.exchange.scanner.services.impl.api.utils.WebClientBuilder;
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
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBitmart implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Bitmart";

    public final static String BASE_ENDPOINT = "https://api-cloud.bitmart.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiBitmart() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/v1/currencies";

        ResponseEntity<BitmartSymbolData> responseEntity = restTemplate.getForEntity(url, BitmartSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bitmart, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bitmart, код: " + statusCode);
        }

        return responseEntity.getBody().getData().getCurrencies().stream()
                .filter(symbol -> symbol.getDepositEnabled() && symbol.getWithdrawEnabled())
                .map(symbol -> CoinFactory.getCoin(symbol.getId()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<BitmartTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiBitmart::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    public Flux<List<BitmartTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "_USDT")
                .toList();

        return webClient.get()
                .uri("/spot/quotation/v3/tickers")
                .retrieve()
                .bodyToMono(BitmartTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMap(response -> {
                    List<List<Object>> rawData = response.getData();
                    List<BitmartTickerData> tickerData = new ArrayList<>();
                    for (List<Object> dataLine : rawData) {
                        BitmartTickerData ticker = new BitmartTickerData();
                        ticker.setSymbol((String) dataLine.get(0));
                        ticker.setQv24h((String) dataLine.get(3));
                        ticker.setBidPx((String) dataLine.get(8));
                        ticker.setAskPx((String) dataLine.get(10));
                        tickerData.add(ticker);
                    }
                    return Mono.just(tickerData);
                })
                .flatMapIterable(ticker -> ticker)
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(BitmartTickerData ticker) {
        return new CoinDataTicker(
                ticker.getSymbol().replaceAll("_", ""),
                ticker.getQv24h(),
                ticker.getBidPx(),
                ticker.getAskPx()
        );
    }

    private static boolean isNotEmptyValues(BitmartTickerData ticker) {
        return ticker.getBidPx() != null &&
                ticker.getAskPx() != null &&
                ticker.getQv24h() != null &&
                !ticker.getBidPx().isEmpty() &&
                !ticker.getAskPx().isEmpty() &&
                !ticker.getQv24h().isEmpty();
    }
}
