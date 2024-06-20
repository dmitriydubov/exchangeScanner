package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bitget.depth.BitgetCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitget.exchangeinfo.BitgetSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bitget.ticker.BitgetTicker;
import com.exchange.scanner.dto.response.exchangedata.bitget.ticker.BitgetTickerData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
public class ApiBitget implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Bitget";

    public final static String BASE_ENDPOINT = "https://api.bitget.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBitget() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        String url = BASE_ENDPOINT + "/api/v2/spot/public/coins";

        ResponseEntity<BitgetSymbolData> responseEntity = restTemplate.getForEntity(url, BitgetSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bitget, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bitget, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .map(symbol -> CoinFactory.getCoin(symbol.getCoin()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<BitgetTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiBitget::getCoinDataTickerDTO)
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
                        .uri(uriBuilder -> uriBuilder.path("/api/v2/spot/market/merge-depth")
                                .queryParam("symbol", coin)
                                .queryParam("limit", DEPTH_REQUEST_LIMIT)
                                .build()
                        )
                        .retrieve()
                        .bodyToFlux(String.class)
                        .onErrorMap(throwable -> {
                            log.error("Ошибка получения информации от " + NAME + ". Причина: {}", throwable.getCause().getMessage(), throwable);
                            return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                        })
                        .map(response -> {
                            try {
                                BitgetCoinDepth bitgetCoinDepth = objectMapper.readValue(response, BitgetCoinDepth.class);
                                bitgetCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getBitgetCoinDepth(bitgetCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<List<BitgetTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/spot/market/tickers")
                        .build()
                )
                .retrieve()
                .bodyToFlux(BitgetTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(BitgetTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(BitgetTickerData ticker) {
        return new CoinDataTicker(
                ticker.getSymbol(),
                ticker.getBaseVolume(),
                ticker.getBidPr(),
                ticker.getAskPr()
        );
    }

    private static boolean isNotEmptyValues(BitgetTickerData ticker) {
        return ticker.getBidPr() != null &&
                ticker.getAskPr() != null &&
                ticker.getBaseVolume() != null &&
                !ticker.getBidPr().isEmpty() &&
                !ticker.getAskPr().isEmpty() &&
                !ticker.getBaseVolume().isEmpty();
    }
}
