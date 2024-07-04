package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.poloniex.chains.PoloniexChain;
import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.poloniex.exchangeinfo.PoloniexSymbolData;
import com.exchange.scanner.dto.response.exchangedata.poloniex.tickervolume.PoloniexVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.poloniex.api.client.model.OrderBook;
import com.poloniex.api.client.rest.PoloRestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiPoloniex implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${exchanges.apiKeys.Poloniex.key}")
    private String key;

    @Value("${exchanges.apiKeys.Poloniex.secret}")
    private String secret;

    private static final String NAME = "Poloniex";

    public final static String BASE_ENDPOINT = "https://api.poloniex.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiPoloniex() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/markets";

        ResponseEntity<PoloniexSymbolData[]> responseEntity = restTemplate.getForEntity(url, PoloniexSymbolData[].class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Poloniex, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Poloniex, код: " + statusCode);
        }

        return Arrays.stream(responseEntity.getBody())
                .filter(symbol -> symbol.getQuoteCurrencyName().equals("USDT") && symbol.getState().equals("NORMAL"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCurrencyName()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();

        coins.forEach(coin -> {
            Map<String, PoloniexChain> response = getChains(coin).block();

            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.forEach((coinKey, responseValue) -> {
                    Chain chain = new Chain();
                    chain.setName(responseValue.getBlockchain());
                    chain.setCommission(new BigDecimal(responseValue.getWithdrawalFee()));
                    chains.add(chain);
                });
                coin.setChains(chains);
                coinsWithChains.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithChains;
    }

    private Mono<Map<String, PoloniexChain>> getChains(Coin coin) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/currencies/{currency}")
                        .build(coin.getName())
                )
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, PoloniexChain>>() {});
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        PoloRestClient poloniexApiClient = new PoloRestClient(BASE_ENDPOINT, key, secret);

        Set<Coin> coinsWithFees = new HashSet<>();

        coins.forEach(coin -> {
            coin.setTakerFee(new BigDecimal(poloniexApiClient.getFeeInfo().getTakerRate()));
            coinsWithFees.add(coin);
        });

        return coinsWithFees;
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            PoloniexVolumeTicker response = getCoinTicker(coin).block();
            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getAmount()));
                coinsWithVolume24h.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithVolume24h;
    }

    private Mono<PoloniexVolumeTicker> getCoinTicker(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/markets/{symbol}/ticker24h")
                        .build(symbol)
                )
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения торгового объёма за 24 часа от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(PoloniexVolumeTicker.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        PoloRestClient poloRestClient = new PoloRestClient(BASE_ENDPOINT);
        Set<CoinDepth> coinDepths = new HashSet<>();
        coins.forEach(coin -> {
            OrderBook response = poloRestClient.getOrderBook(coin + "_USDT", "0.01", DEPTH_REQUEST_LIMIT);
            PoloniexCoinDepth poloniexCoinDepth = new PoloniexCoinDepth();
            poloniexCoinDepth.setCoinName(coin);
            poloniexCoinDepth.setAsks(response.getAsks());
            poloniexCoinDepth.setBids(response.getBids());
            CoinDepth coinDepth = ApiExchangeUtils.getPoloniexCoinDepth(poloniexCoinDepth);
            coinDepths.add(coinDepth);
            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepths;
    }
}
