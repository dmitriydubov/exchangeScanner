package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.kucoin.chains.KucoinChainResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tradingfee.KucoinTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.exchangeinfo.KucoinSymbolData;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume.KucoinTickerVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
import com.exchange.scanner.services.utils.ListUtils;
import com.exchange.scanner.services.utils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class ApiKucoin implements ApiExchange {

    @Value("${exchanges.apiKeys.Kucoin.key}")
    private String key;

    @Value("${exchanges.apiKeys.Kucoin.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.Kucoin.passphrase}")
    private String passphrase;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Kucoin";

    public final static String BASE_ENDPOINT = "https://api.kucoin.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiKucoin() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/api/v2/symbols";

        ResponseEntity<KucoinSymbolData> responseEntity = restTemplate.getForEntity(url, KucoinSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Kucoin, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Kucoin, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getQuoteCurrency().equals("USDT") && symbol.getEnableTrading())
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCurrency()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        coins.forEach(coin -> {
            List<List<Chain>> response = getChain(coin).collectList().block();
            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.forEach(chains::addAll);
                coin.setChains(chains);
                coinsWithChains.add(coin);
            } else {
                log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            }
        });

        return coinsWithChains;
    }

    private Flux<List<Chain>> getChain(Coin coin) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/currencies/{currency}")
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
                .bodyToFlux(KucoinChainResponse.class)
                .map(response -> response.getData().getChains().stream()
                        .filter(chainDto -> chainDto.getIsDepositEnabled() && chainDto.getIsWithdrawEnabled())
                        .map(dto -> {
                           Chain chain = new Chain();
                           chain.setName(dto.getChainName());
                           chain.setCommission(new BigDecimal(dto.getWithdrawalMinFee()));
                           return chain;
                        })
                        .toList()
                );
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithFees = new HashSet<>();
        KucoinTradingFeeResponse response = getFee(coins).blockLast();

        if (response == null) return coinsWithFees;

        coins.forEach(coin -> {
            response.getData().forEach(responseFee -> {
                if (coin.getName().equals(responseFee.getSymbol().replaceAll("-USDT", ""))) {
                    coin.setTakerFee(new BigDecimal(responseFee.getTakerFeeRate()));
                    coinsWithFees.add(coin);
                }
            });
        });

        return coinsWithFees;
    }

    private Flux<KucoinTradingFeeResponse> getFee(Set<Coin> coins) {
        int maxSymbolsSizePerRequest = 10;
        List<Coin> coinList = new ArrayList<>(coins);
        List<List<Coin>> partitions = ListUtils.partition(coinList, maxSymbolsSizePerRequest);
        String endpoint = "/api/v1/trade-fees";

        return Flux.fromIterable(partitions)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(partition -> {
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String strToSign = timestamp + "GET" + endpoint + "?symbols=" + generateParameters(partition);
                    String signature = ApiExchangeUtils.generateKucoinSignature(secret, strToSign);
                    String encodedPassphrase = ApiExchangeUtils.generateKucoinPassphrase(secret, passphrase);
                    return webClient
                            .get()
                            .uri(uriBuilder -> uriBuilder
                                    .path(endpoint)
                                    .queryParam("symbols", generateParameters(partition))
                                    .build()
                            )
                            .header("KC-API-KEY", key)
                            .header("KC-API-SIGN", signature)
                            .header("KC-API-TIMESTAMP", timestamp)
                            .header("KC-API-PASSPHRASE", encodedPassphrase)
                            .header("KC-API-KEY-VERSION", "3")
                            .retrieve()
                            .onStatus(
                                    status -> status.is4xxClientError() || status.is5xxServerError(),
                                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                        log.error("Ошибка получения торговых комиссии от " + NAME + ". Причина: {}", errorBody);
                                        return Mono.empty();
                                    })
                            )
                            .bodyToFlux(KucoinTradingFeeResponse.class);
                });

    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            KucoinTickerVolumeResponse response = getCoinTickerVolume(coin).block();
            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getData().getVolValue()));
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

    private Mono<KucoinTickerVolumeResponse> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "-USDT";

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/market/stats")
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
                .bodyToMono(KucoinTickerVolumeResponse.class);
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
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/market/orderbook/level2_" + DEPTH_REQUEST_LIMIT)
                                .queryParam("symbol", coin)
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
                        .bodyToFlux(String.class)
                        .map(response -> {
                            try {
                                KucoinCoinDepth kucoinCoinDepth = objectMapper.readValue(response, KucoinCoinDepth.class);
                                kucoinCoinDepth.setCoinName(coin.replaceAll("-USDT", ""));

                                return ApiExchangeUtils.getKucoinCoinDepth(kucoinCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
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
