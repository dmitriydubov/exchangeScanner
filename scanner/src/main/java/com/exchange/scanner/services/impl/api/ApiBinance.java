package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.binance.ticker.BinanceCoinTicker;
import com.exchange.scanner.dto.response.exchangedata.binance.exchangeinfo.ExchangeInfo;
import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.ListUtils;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBinance implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Binance";

    private static final String BASE_ENDPOINT = "https://api.binance.com";

    private static final int TIMEOUT = 10000;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiBinance() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        String url = BASE_ENDPOINT + "/api/v3/exchangeInfo";

        ResponseEntity<ExchangeInfo> responseEntity = restTemplate.getForEntity(url, ExchangeInfo.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Binance, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Binance, код: " + statusCode);
        }

        return responseEntity.getBody().getSymbols().stream()
                .filter(symbol -> symbol.getQuoteAsset().equals("USDT") &&
                        symbol.getStatus().equals("TRADING") &&
                        symbol.getIsSpotTradingAllowed()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseAsset()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<CoinDataTicker> response = getCoinTicker(new ArrayList<>(coins))
                .map(ApiBinance::getCoinDataTickerDTO);

        List<CoinDataTicker> coinDataTickers = response
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
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v3/depth")
                                .queryParam("symbol", coin)
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
                                BinanceCoinDepth binanceCoinDepth = objectMapper.readValue(response, BinanceCoinDepth.class);
                                binanceCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getBinanceCoinDepth(binanceCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }


    private Flux<BinanceCoinTicker> getCoinTicker(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);
        return Flux.fromIterable(partitions)
                .flatMap(partition -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v3/ticker/24hr")
                                .queryParam("symbols", generateParameters(partition))
                                .build()
                        )
                        .retrieve()
                        .bodyToFlux(BinanceCoinTicker.class))
                        .onErrorMap(throwable -> {
                            log.error("Ошибка получения информации от " + NAME, throwable);
                            return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                        })
                .filter(ApiBinance::isNotEmptyValues);
    }

    private static CoinDataTicker getCoinDataTickerDTO(BinanceCoinTicker ticker) {
        return new CoinDataTicker(
                ticker.getSymbol(),
                ticker.getVolume(),
                ticker.getBidPrice(),
                ticker.getAskPrice()
        );
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        coins.forEach(coin -> sb.append("\"").append(coin.getSymbol()).append("USDT").append("\"").append(","));
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        parameters = sb.toString();

        return parameters;
    }

    private static boolean isNotEmptyValues(BinanceCoinTicker ticker) {
        return ticker.getBidPrice() != null &&
                ticker.getAskPrice() != null &&
                ticker.getVolume() != null &&
                !ticker.getBidPrice().isEmpty() &&
                !ticker.getAskPrice().isEmpty() &&
                !ticker.getVolume().isEmpty();
    }
}
