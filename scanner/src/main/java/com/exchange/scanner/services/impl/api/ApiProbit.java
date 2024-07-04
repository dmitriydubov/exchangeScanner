package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.probit.chains.Data;
import com.exchange.scanner.dto.response.exchangedata.probit.chains.ProbitChainData;
import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.probit.tradingfee.FeeData;
import com.exchange.scanner.dto.response.exchangedata.probit.tradingfee.ProbitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.probit.exchangeinfo.ProbitSymbolData;
import com.exchange.scanner.dto.response.exchangedata.probit.tickervolume.ProbitTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.ListUtils;
import com.exchange.scanner.services.utils.WebClientBuilder;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiProbit implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Probit";

    public final static String BASE_ENDPOINT = "https://api.probit.com/api/exchange/v1";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private final WebClient webClient;

    public ApiProbit() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/market";

        ResponseEntity<ProbitSymbolData> responseEntity = restTemplate.getForEntity(url, ProbitSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Probit, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Probit, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol ->
                        symbol.getQuoteCurrencyId().equals("USDT") &&
                        !symbol.getClosed()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCurrencyId()))
                .collect(Collectors.toSet());
    }

    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());

        ProbitChainData response = getChainResponse().block();
        if (response == null) {
            log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            return coinsWithChains;
        }
        List<Data> data = response.getData().stream()
                .filter(coinResponse -> coinsNames.contains(coinResponse.getId()))
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            data.forEach(dtoResponseElement -> {
                if (coin.getName().equals(dtoResponseElement.getId())) {
                    dtoResponseElement.getWithdrawalFee().forEach(chainsDto -> {
                        Chain chain = new Chain();
                        chain.setName(chainsDto.getCurrencyId());
                        chain.setCommission(new BigDecimal(chainsDto.getAmount()));
                        chains.add(chain);
                    });
                }
            });
            coin.setChains(chains);
            coinsWithChains.add(coin);
        });

        return coinsWithChains;
    }

    private Mono<ProbitChainData> getChainResponse() {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/currency")
                        .build()
                )
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(ProbitChainData.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithFee = new HashSet<>();
        Set<String> symbols = coins.stream().map(coin -> coin.getName() + "-USDT").collect(Collectors.toSet());

        ProbitTradingFeeResponse response = getFee().block();

        if (response == null) return coinsWithFee;
        List<FeeData> data = response.getData().stream()
                .filter(feeData -> symbols.contains(feeData.getId()))
                .toList();

        coins.forEach(coin -> {
            data.forEach(feeData -> {
                if (coin.getName().equals(feeData.getBaseCurrencyId())) {
                    coin.setTakerFee(new BigDecimal(feeData.getTakerFeeRate()));
                    coinsWithFee.add(coin);
                }
            });
        });

        return coinsWithFee;
    }

    private Mono<ProbitTradingFeeResponse> getFee() {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/market")
                        .build()
                )
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(ProbitTradingFeeResponse.class);
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        ProbitTickerVolume response = getCoinTickerVolume(new ArrayList<>(coins)).blockLast();

        if (response == null) return coinsWithVolume24h;

        coins.forEach(coin -> {
            response.getData().forEach(data -> {
                if (coin.getName().equals(data.getMarketId().replaceAll("-USDT", ""))) {
                    coin.setVolume24h(new BigDecimal(data.getQuoteVolume()));
                    coinsWithVolume24h.add(coin);
                }
            });
        });

        return coinsWithVolume24h;
    }

    private Flux<ProbitTickerVolume> getCoinTickerVolume(List<Coin> coins) {
        int maxRequestSymbolsSize = 20;
        List<List<Coin>> partitions = ListUtils.partition(coins, 20);

        return Flux.fromIterable(partitions)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(partition -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/ticker")
                                .queryParam("market_ids", generateParameters(partition))
                                .build()
                        )
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                    log.error("Ошибка получения торгового объёма за 24 часа от " + NAME + ". Причина: {}", errorBody);
                                    return Mono.empty();
                                })
                        )
                        .bodyToFlux(ProbitTickerVolume.class)
                );
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }

    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin + "-USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/order_book")
                                .queryParam("market_id", coin)
                                .build()
                        )
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                    log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                                    return Mono.empty();
                                })
                        )
                        .bodyToFlux(ProbitCoinDepth.class)
                        .map(response -> {
                            response.setCoinName(coin.replaceAll("-USDT", ""));
                            return ApiExchangeUtils.getProbitCoinDepth(response);
                        })
                );
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getSymbol()).append("-USDT").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
