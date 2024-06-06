package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bitget.exchangeinfo.BitgetSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bitget.ticker.BitgetTicker;
import com.exchange.scanner.dto.response.exchangedata.bitget.ticker.BitgetTickerData;
import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
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
public class ApiBitget implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "Bitget";

    public final static String BASE_ENDPOINT = "https://api.bitget.com";

    private static final int TIMEOUT = 10000;

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
