package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.xt.exchangeinfo.XTSymbolData;
import com.exchange.scanner.dto.response.exchangedata.xt.ticker.XTTicker;
import com.exchange.scanner.dto.response.exchangedata.xt.ticker.XTTickerResult;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.impl.api.utils.CoinFactory;
import com.exchange.scanner.services.impl.api.utils.ListUtils;
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
public class ApiXT implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "XT";

    public final static String BASE_ENDPOINT = "https://sapi.xt.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiXT() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/v4/public/symbol";

        ResponseEntity<XTSymbolData> responseEntity = restTemplate.getForEntity(url, XTSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от XT, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от XT, код: " + statusCode);
        }

        return responseEntity.getBody().getResult().getSymbols().stream()
                .filter(symbol -> symbol.getTradingEnabled() && symbol.getState().equals("ONLINE"))
                .map(symbol -> {
                    String coinName = symbol.getBaseCurrency().toUpperCase();
                    return CoinFactory.getCoin(coinName);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<XTTickerResult> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiXT::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    private Flux<List<XTTickerResult>> getCoinTicker(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);

        return Flux.fromIterable(partitions)
                .flatMap(partition -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/v4/public/ticker")
                                .queryParam("symbols", generateParameters(partition))
                                .build()
                        )
                        .retrieve()
                        .bodyToFlux(XTTicker.class))
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(XTTicker::getResult)
                .filter(ApiXT::isNotEmptyValue)
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(XTTickerResult ticker) {
        return new CoinDataTicker(
                ticker.getS().toUpperCase().replaceAll("_", ""),
                ticker.getV(),
                ticker.getBp(),
                ticker.getAp()
        );
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getSymbol().toLowerCase())
                .append("_usdt")
                .append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }

    private static boolean isNotEmptyValue(XTTickerResult result) {
        return result.getBp() != null &&
                result.getAp() != null &&
                result.getV() != null &&
                !result.getBp().isEmpty() &&
                !result.getAp().isEmpty() &&
                !result.getV().isEmpty();
    }
}
