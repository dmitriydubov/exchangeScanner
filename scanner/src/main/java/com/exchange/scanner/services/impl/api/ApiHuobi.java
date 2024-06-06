package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.huobi.exchangeinfo.HuobiSymbolData;
import com.exchange.scanner.dto.response.exchangedata.huobi.ticker.HuobiTicker;
import com.exchange.scanner.dto.response.exchangedata.huobi.ticker.HuobiTickerData;
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
public class ApiHuobi implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "Huobi";

    public final static String BASE_ENDPOINT = "https://api.huobi.pro";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiHuobi() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/v2/settings/common/currencies";

        ResponseEntity<HuobiSymbolData> responseEntity = restTemplate.getForEntity(url, HuobiSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Huobi, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Huobi, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getDe() && symbol.getWed())
                .map(symbol -> CoinFactory.getCoin(symbol.getDn()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<HuobiTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiHuobi::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    private Flux<List<HuobiTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol().toLowerCase() + "usdt")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/market/tickers")
                        .build()
                )
                .retrieve()
                .bodyToFlux(HuobiTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(HuobiTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getSymbol()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(HuobiTickerData ticker) {
        return new CoinDataTicker(
                ticker.getSymbol().toUpperCase(),
                ticker.getVol(),
                ticker.getBid(),
                ticker.getAsk()
        );
    }

    private static boolean isNotEmptyValues(HuobiTickerData ticker) {
        return ticker.getBid() != null &&
                ticker.getAsk() != null &&
                ticker.getVol() != null &&
                !ticker.getBid().isEmpty() &&
                !ticker.getAsk().isEmpty() &&
                !ticker.getVol().isEmpty();
    }
}
