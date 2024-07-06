package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinex.chains.CoinexChainsData;
import com.exchange.scanner.dto.response.exchangedata.coinex.chains.CoinexChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinex.tickervolume.CoinExVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.coinex.coins.CoinExCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.coinex.tradingfee.CoinexTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.Coinex.CoinExCoinDepthBuilder;
import com.exchange.scanner.services.utils.Coinex.CoinexSignatureBuilder;
import com.exchange.scanner.services.utils.ListUtils;
import com.exchange.scanner.services.utils.WebClientBuilder;
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
public class ApiCoinEx implements ApiExchange {

    @Value("${exchanges.apiKeys.Coinex.key}")
    private String key;

    @Value("${exchanges.apiKeys.Coinex.secret}")
    private String secret;

    private static final String NAME = "CoinEx";

    public final static String BASE_ENDPOINT = "https://api.coinex.com/v2";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 25;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final int REQUEST_INTERVAL = 0;

    private final WebClient webClient;

    public ApiCoinEx() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        CoinExCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQuoteCcy().equals("USDT"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCcy()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<CoinExCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/market")
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
            .bodyToMono(CoinExCurrencyResponse.class);
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        CoinexChainsResponse response = getChains().block();

        if (response == null) return coinsWithChains;
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());
        List<CoinexChainsData> filteredData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getAsset().getCcy()))
                .toList();
        coins.forEach(coin -> {
            filteredData.forEach(data -> {
                if (coin.getName().equals(data.getAsset().getCcy())) {
                    Set<Chain> chains = new HashSet<>();
                    data.getChains().forEach(chainResponse -> {
                        Chain chain = new Chain();
                        chain.setName(chainResponse.getChain());
                        chain.setCommission(new BigDecimal(chainResponse.getWithdrawalFee()));
                        chains.add(chain);
                    });
                    coin.setChains(chains);
                }
            });
            coinsWithChains.add(coin);
        });

        return coinsWithChains;
    }

    private Mono<CoinexChainsResponse> getChains() {
        String requestPath = "/assets/all-deposit-withdraw-config";
        Map<String, String> params = new HashMap<>();
        CoinexSignatureBuilder signatureBuilder = new CoinexSignatureBuilder(secret, params);
        signatureBuilder.createSignature("GET", requestPath);

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                return uriBuilder.build();
            })
            .header("X-COINEX-KEY", key)
            .header("X-COINEX-SIGN", signatureBuilder.getSignature())
            .header("X-COINEX-TIMESTAMP", signatureBuilder.getTimestamp())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinexChainsResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithTradingFees = new HashSet<>();

        coins.forEach(coin -> {
            CoinexTradingFeeResponse response = getFee(coin).block();

            if (response != null) {
                coin.setTakerFee(new BigDecimal(response.getData().getFirst().getTakerFeeRate()));
                coinsWithTradingFees.add(coin);
            }
        });

        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        return coinsWithTradingFees;
    }

    private Mono<CoinexTradingFeeResponse> getFee(Coin coin) {
        String symbol = coin.getSymbol() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/market")
                    .queryParam("market", symbol)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinexTradingFeeResponse.class);
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        CoinExVolumeTicker response = getCoinTicker(new ArrayList<>(coins)).blockLast();

        if (response == null) return coinsWithVolume24h;
        coins.forEach(coin -> {
            response.getData().forEach(responseData -> {
                if (coin.getName().equals(responseData.getMarket().replaceAll("USDT", ""))) {
                    coin.setVolume24h(new BigDecimal(responseData.getValue()));
                    coinsWithVolume24h.add(coin);
                }
            });
        });

        return coinsWithVolume24h;
    }

    private Flux<CoinExVolumeTicker> getCoinTicker(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);

        return Flux.fromIterable(partitions)
            .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
            .flatMap(partition -> webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/spot/ticker")
                        .queryParam("market", generateParameters(partition))
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
                .bodyToFlux(CoinExVolumeTicker.class));
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            CoinExCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = CoinExCoinDepthBuilder.getCoinDepth(coin, response.getData().getDepth());
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

    private Mono<CoinExCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/spot/depth")
                    .queryParam("market", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
                    .queryParam("interval", REQUEST_INTERVAL)
                    .build())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinExCoinDepth.class);
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters = "";
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> {
            sb.append(coin.getName()).append("USDT").append(",");
        });
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
