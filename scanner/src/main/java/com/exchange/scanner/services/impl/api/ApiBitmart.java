package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bitmart.chains.BitmartChainsCurrencies;
import com.exchange.scanner.dto.response.exchangedata.bitmart.chains.BitmartChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.bitmart.depth.BitmartCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitmart.coins.BitmartCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bitmart.tickervolume.BitmartVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.bitmart.tradingfee.BitmartTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.Bitmart.BitmartCoinDepthBuilder;
import com.exchange.scanner.services.utils.Bitmart.BitmartSignatureBuilder;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBitmart implements ApiExchange {

    @Value("${exchanges.apiKeys.Bitmart.key}")
    private String key;

    @Value("${exchanges.apiKeys.Bitmart.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.Bitmart.memo}")
    private String memo;

    private static final String NAME = "Bitmart";

    public final static String BASE_ENDPOINT = "https://api-cloud.bitmart.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiBitmart() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        BitmartCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getData().getSymbols().stream()
                .filter(symbol -> symbol.getQuoteCurrency().equals("USDT") && symbol.getTradeStatus().equals("trading"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCurrency()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BitmartCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v1/symbols/details")
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
            .bodyToMono(BitmartCurrencyResponse.class);
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        BitmartChainsResponse response = getChains().block();

        if (response == null) return coinsWithChains;
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());
        List<BitmartChainsCurrencies> chainsCurrencies = response.getData().getCurrencies().stream()
                .filter(chainResponse -> coinsNames.contains(chainResponse.getCurrency()))
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainsCurrencies.forEach(responseChain -> {
                if (coin.getName().equals(responseChain.getCurrency())) {
                    Chain chain = new Chain();
                    chain.setName(responseChain.getCurrency());
                    chain.setCommission(new BigDecimal(responseChain.getWithdrawMinFee()));
                    chains.add(chain);
                }
            });
            coin.setChains(chains);
            coinsWithChains.add(coin);
        });

        return coinsWithChains;
    }

    private Mono<BitmartChainsResponse> getChains() {
        String requestPath = "/account/v1/currencies";
        BitmartSignatureBuilder signatureBuilder = new BitmartSignatureBuilder(secret, memo);
        signatureBuilder.createSignature("GET");

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .build()
            )
            .header("X-BM-TIMESTAMP", signatureBuilder.getTimestamp())
            .header("X-BM-KEY", key)
            .header("X-BM-SIGN", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitmartChainsResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithTradingFees = new HashSet<>();

        coins.forEach(coin -> {
            BitmartTradingFeeResponse response = getFee(coin).block();

            if (response != null) {
                coin.setTakerFee(new BigDecimal(response.getData().getTakerFee()));
                coinsWithTradingFees.add(coin);
            }
        });

        return coinsWithTradingFees;
    }

    private Mono<BitmartTradingFeeResponse> getFee(Coin coin) {
        String requestPath = "/spot/v1/trade_fee";
        String symbol = coin.getName() + "_USDT";
        BitmartSignatureBuilder signatureBuilder = new BitmartSignatureBuilder(secret, memo);
        signatureBuilder.createSignature("GET", Collections.singletonMap("symbol", symbol));

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .queryParam("symbol", symbol)
                    .build()
            )
            .header("X-BM-TIMESTAMP", signatureBuilder.getTimestamp())
            .header("X-BM-KEY", key)
            .header("X-BM-SIGN", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitmartTradingFeeResponse.class);
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            BitmartVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getData().getQv24h()));
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

    public Mono<BitmartVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getSymbol() + "_USDT";

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/quotation/v3/ticker")
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
            .bodyToMono(BitmartVolumeTicker.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            BitmartCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = BitmartCoinDepthBuilder.getCoinDepth(coin, response.getData());
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

    private Mono<BitmartCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/spot/quotation/v3/books")
                    .queryParam("symbol", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
                    .build())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitmartCoinDepth.class);
    }
}
