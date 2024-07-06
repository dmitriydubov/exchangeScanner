package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bybit.chains.BybitChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.coins.BybitCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.tickervolume.BybitCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee.BybitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.Bybit.BybitCoinDepthBuilder;
import com.exchange.scanner.services.utils.Bybit.BybitSignatureBuilder;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBybit implements ApiExchange {

    @Value("${exchanges.apiKeys.Bybit.key}")
    private String key;

    @Value("${exchanges.apiKeys.Bybit.secret}")
    private String secret;

    private static final String NAME = "Bybit";

    public static final String BASE_ENDPOINT = "https://api.bybit.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBybit() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        BybitCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getResult().getList().stream()
                .filter(symbol -> symbol.getShowStatus().equals("1") && symbol.getQuoteCoin().equals("USDT"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCoin()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BybitCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v3/public/symbols")
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
            .bodyToMono(BybitCurrencyResponse.class);
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();

        coins.forEach(coin -> {
            BybitChainsResponse response = getChains(coin).block();

            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.getResult().getRows().getFirst().getChains().forEach(chainResponse -> {
                    Chain chain = new Chain();
                    chain.setName(chainResponse.getChain().toUpperCase());
                    chain.setCommission(new BigDecimal(chainResponse.getWithdrawFee()));
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

    private Mono<BybitChainsResponse> getChains(Coin coin) {
        String timestamp = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
        String recv = "5000";
        String paramStr = "coin=" + coin.getName();
        String stringToSign = timestamp + key + recv + paramStr;
        String sign = BybitSignatureBuilder.generateBybitSignature(stringToSign, secret);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v5/asset/coin/query-info")
                    .queryParam("coin", coin.getName())
                    .build()
            )
            .header("X-BAPI-SIGN", sign)
            .header("X-BAPI-API-KEY", key)
            .header("X-BAPI-TIMESTAMP", timestamp)
            .header("X-BAPI-RECV-WINDOW", recv)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BybitChainsResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithTradingFee = new HashSet<>();

        coins.forEach(coin -> {
            BybitTradingFeeResponse response = getFee(coin).block();
            if (response != null) {
                coin.setTakerFee(new BigDecimal(response.getResult().getList().getFirst().getTakerFeeRate()));
                coinsWithTradingFee.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithTradingFee;
    }

    private Mono<BybitTradingFeeResponse> getFee(Coin coin) {
        String symbol = coin.getName() + "USDT";
        String timestamp = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
        String recv = "5000";
        String paramStr = "category=spot" + "&" + "symbol=" + symbol;
        String stringToSign = timestamp + key + recv + paramStr;
        String sign = BybitSignatureBuilder.generateBybitSignature(stringToSign, secret);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v5/account/fee-rate")
                    .queryParam("category", "spot")
                    .queryParam("symbol", symbol)
                    .build()
            )
            .header("X-BAPI-SIGN", sign)
            .header("X-BAPI-API-KEY", key)
            .header("X-BAPI-TIMESTAMP", timestamp)
            .header("X-BAPI-RECV-WINDOW", recv)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BybitTradingFeeResponse.class);
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            BybitCoinTickerVolume response = getCoinTickerVolume(coin).block();
            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getResult().getQv()));
                coinsWithVolume24h.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw  new RuntimeException();
            }
        });

        return coinsWithVolume24h;
    }

    private Mono<BybitCoinTickerVolume> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v3/public/quote/ticker/24hr")
                    .queryParam("symbol", symbol)
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
            .bodyToMono(BybitCoinTickerVolume.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            BybitCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = BybitCoinDepthBuilder.getCoinDepth(coin, response.getResult());
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

    private Mono<BybitCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/spot/v3/public/quote/depth")
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
            .bodyToMono(BybitCoinDepth.class);
    }
}
