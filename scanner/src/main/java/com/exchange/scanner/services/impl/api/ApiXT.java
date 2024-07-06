package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.xt.chains.XTChainResponse;
import com.exchange.scanner.dto.response.exchangedata.xt.chains.XTChainResult;
import com.exchange.scanner.dto.response.exchangedata.xt.depth.XTCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.xt.coins.XTCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.xt.tickervolume.XTVolumeTicker;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.ListUtils;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.exchange.scanner.services.utils.XT.XTCoinDepthBuilder;
import com.exchange.scanner.services.utils.XT.XTSignatureBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class ApiXT implements ApiExchange {

    @Value("${exchanges.apiKeys.XT.key}")
    private String key;

    @Value("${exchanges.apiKeys.XT.secret}")
    private String secret;

    private static final String NAME = "XT";

    public final static String BASE_ENDPOINT = "https://sapi.xt.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiXT() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        XTCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getResult().getSymbols().stream()
                .filter(symbol ->
                        symbol.getQuoteCurrency().equals("usdt") &&
                                symbol.getTradingEnabled() &&
                                symbol.getState().equals("ONLINE")
                )
                .map(symbol -> {
                    String coinName = symbol.getBaseCurrency().toUpperCase();
                    return CoinFactory.getCoin(coinName);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<XTCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v4/public/symbol")
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
            .bodyToMono(XTCurrencyResponse.class);
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();

        XTChainResponse response = getChains().block();

        if (response == null) return coinsWithChains;

        Set<String> coinsNames = coins.stream()
                .map(Coin::getName)
                .collect(Collectors.toSet());

        coinsNames.forEach(System.out::println);
        List<XTChainResult> xtChainResultListFiltered = response.getResult().stream()
                .filter(result -> coinsNames.contains(result.getCurrency().toUpperCase()))
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            xtChainResultListFiltered.forEach(result -> {
                if (coin.getName().equals(result.getCurrency().toUpperCase())) {
                    result.getSupportChains().stream()
                        .filter(chainResponse -> chainResponse.getDepositEnabled() && chainResponse.getWithdrawEnabled())
                        .forEach(chainResponse -> {
                            Chain chain = new Chain();
                            chain.setName(chainResponse.getChain());
                            chain.setCommission(new BigDecimal(chainResponse.getWithdrawFeeAmount()));
                            chains.add(chain);
                        });
                }
            });
            coin.setChains(chains);
            coinsWithChains.add(coin);
        });


        return coinsWithChains;
    }

    private Mono<XTChainResponse> getChains() {
        String requestPath = "/v4/public/wallet/support/currency";
        TreeMap<String, String> params = new TreeMap<>();
        XTSignatureBuilder signatureBuilder = new XTSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature("GET", requestPath);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .build()
            )
            .headers(httpHeaders -> {
                signatureBuilder.getHeaders().forEach(httpHeaders::add);
            })
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(XTChainResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsFee = new HashSet<>();

        coins.forEach(coin -> {
            coin.setTakerFee(BigDecimal.ZERO);
            coinsFee.add(coin);
        });

        return coinsFee;
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        XTVolumeTicker response = getCoinTickerVolume(new ArrayList<>(coins)).blockLast();

        if (response == null) return  coinsWithVolume24h;
        coins.forEach(coin -> {
            response.getResult().stream()
                .filter(responseData -> responseData.getS().endsWith("_usdt"))
                .forEach(responseData -> {
                    if (coin.getName().equals(responseData.getS().replaceAll("_usdt", "").toUpperCase())) {
                        coin.setVolume24h(new BigDecimal(responseData.getV()));
                        coinsWithVolume24h.add(coin);
                    }
                });
        });

        return coinsWithVolume24h;
    }

    private Flux<XTVolumeTicker> getCoinTickerVolume(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);

        return Flux.fromIterable(partitions)
            .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
            .flatMap(partition -> webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/v4/public/ticker")
                        .queryParam("symbols", generateParameters(partition))
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
                .bodyToFlux(XTVolumeTicker.class));
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            XTCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = XTCoinDepthBuilder.getCoinDepth(coin, response.getResult());
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

    private Mono<XTCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName.toLowerCase() + "_usdt";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/v4/public/depth")
                    .queryParam("symbol", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
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
            .bodyToMono(XTCoinDepth.class);
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName().toLowerCase())
                .append("_usdt")
                .append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
