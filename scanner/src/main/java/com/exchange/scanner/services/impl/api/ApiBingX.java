package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.ticker.BingXTicker;
import com.exchange.scanner.dto.response.exchangedata.bingx.ticker.BingXTickerData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.bingx.exchangeinfo.BingXSymbolData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiBingX implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "BingX";

    public final static String BASE_ENDPOINT = "https://open-api.bingx.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final String TYPE_REQUEST = "step0";

    private final WebClient webClient;

    public ApiBingX() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/openApi/spot/v1/common/symbols";

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от BingX, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от BingX, код: " + statusCode);
        }

        try {
            BingXSymbolData data = objectMapper.readValue(responseEntity.getBody(), BingXSymbolData.class);
            return data.getData().getSymbols().stream()
                    .filter(symbol -> symbol.getStatus() == 1)
                    .map(symbol -> {
                        String coinName = CoinFactory.refactorToStandardCoinName(symbol.getSymbol(), "-");
                        return CoinFactory.getCoin(coinName);
                    })
                    .collect(Collectors.toSet());
        } catch (IOException ex) {
            log.error("Ошибка десериализации ответа от BingX", ex);
            throw new RuntimeException("Ошибка десериализации ответа от BingX", ex);
        }
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<BingXTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiBingX::getCoinDataTickerDTO)
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
                        .uri(uriBuilder -> uriBuilder.path("/openApi/spot/v2/market/depth")
                                .queryParam("symbol", coin)
                                .queryParam("depth", DEPTH_REQUEST_LIMIT)
                                .queryParam("type", TYPE_REQUEST)
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
                                BingXCoinDepth bingXCoinDepth = objectMapper.readValue(response, BingXCoinDepth.class);
                                bingXCoinDepth.setCoinName(coin.replaceAll("_USDT", ""));

                                return ApiExchangeUtils.getBingXCoinDepth(bingXCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private Flux<List<BingXTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "-USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/openApi/spot/v1/ticker/24hr")
                        .queryParam("timestamp", new Timestamp(System.currentTimeMillis()).getTime())
                        .build()
                )
                .retrieve()
                .bodyToFlux(BingXTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(BingXTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(BingXTickerData ticker) {
        return new CoinDataTicker(
                ticker.getSymbol().replaceAll("-", ""),
                ticker.getVolume(),
                ticker.getBidPrice(),
                ticker.getAskPrice()
        );
    }

    private static boolean isNotEmptyValues(BingXTickerData ticker) {
        return ticker.getBidPrice() != null &&
                ticker.getAskPrice() != null &&
                ticker.getVolume() != null &&
                !ticker.getBidPrice().isEmpty() &&
                !ticker.getAskPrice().isEmpty() &&
                !ticker.getVolume().isEmpty();
    }
}
