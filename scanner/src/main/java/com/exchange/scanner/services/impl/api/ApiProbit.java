package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.probit.chains.Data;
import com.exchange.scanner.dto.response.exchangedata.probit.chains.ProbitChainData;
import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.probit.tradingfee.FeeData;
import com.exchange.scanner.dto.response.exchangedata.probit.tradingfee.ProbitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.probit.coins.ProbitCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.probit.tickervolume.ProbitTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.ListUtils;
import com.exchange.scanner.services.utils.Probit.ProbitCoinDepthBuilder;
import com.exchange.scanner.services.utils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
        Set<Coin> coins = new HashSet<>();

        ProbitCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getData().stream()
            .filter(symbol ->
                    symbol.getQuoteCurrencyId().equals("USDT") && !symbol.getClosed()
            )
            .map(symbol -> CoinFactory.getCoin(symbol.getBaseCurrencyId()))
            .collect(Collectors.toSet());

        return coins;
    }

    private Mono<ProbitCurrencyResponse> getCurrencies() {
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
                        log.error("Ошибка получения списка валют. Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(ProbitCurrencyResponse.class);
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
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            ProbitCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = ProbitCoinDepthBuilder.getCoinDepth(coin, response.getData());
                coinDepthSet.add(coinDepth);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepthSet;
    }

    private Mono<ProbitCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "-USDT";
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/order_book")
                    .queryParam("market_id", symbol)
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
            .bodyToMono(ProbitCoinDepth.class);
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName()).append("-USDT").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
