package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.probit.exchangeinfo.ProbitSymbolData;
import com.exchange.scanner.dto.response.exchangedata.probit.ticker.ProbitTicker;
import com.exchange.scanner.dto.response.exchangedata.probit.ticker.ProbitTickerData;
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
public class ApiProbit implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NAME = "Probit";

    public final static String BASE_ENDPOINT = "https://api.probit.com/api/exchange/v1";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiProbit() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/currency";

        ResponseEntity<ProbitSymbolData> responseEntity = restTemplate.getForEntity(url, ProbitSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Probit, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Probit, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getShowInUI() &&
                        !symbol.getDepositSuspended() &&
                        !symbol.getWithdrawalSuspended()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getId()))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins) {
        Flux<ProbitTickerData> response = getCoinTicker(new ArrayList<>(coins))
                .flatMapIterable(result -> result);

        List<CoinDataTicker> coinDataTickers = response
                .map(ApiProbit::getCoinDataTickerDTO)
                .collectList()
                .block();

        return Collections.singletonMap(NAME, coinDataTickers);
    }

    private Flux<List<ProbitTickerData>> getCoinTicker(List<Coin> coins) {
        List<String> coinsSymbols = coins.stream()
                .map(coin -> coin.getSymbol() + "-USDT")
                .toList();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ticker")
                        .build()
                )
                .retrieve()
                .bodyToFlux(ProbitTicker.class)
                .onErrorMap(throwable -> {
                    log.error("Ошибка получения информации от " + NAME, throwable);
                    return new RuntimeException("Ошибка получения информации от " + NAME, throwable);
                })
                .flatMapIterable(ProbitTicker::getData)
                .filter(ticker -> coinsSymbols.contains(ticker.getMarketId()) && isNotEmptyValues(ticker))
                .collectList()
                .flux();
    }

    private static CoinDataTicker getCoinDataTickerDTO(ProbitTickerData ticker) {
        return new CoinDataTicker(
                ticker.getMarketId().replaceAll("-", ""),
                ticker.getQuoteVolume(),
                ticker.getLast(),
                ticker.getLast()
        );
    }

    private static boolean isNotEmptyValues(ProbitTickerData ticker) {
        return ticker.getLast() != null &&
                ticker.getQuoteVolume() != null &&
                !ticker.getLast().isEmpty() &&
                !ticker.getQuoteVolume().isEmpty();
    }
}
