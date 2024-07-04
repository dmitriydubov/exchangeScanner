package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bybit.chains.BybitChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.exchangeinfo.BybitSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bybit.tickervolume.BybitCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee.BybitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.ApiExchangeUtils;
import com.exchange.scanner.services.utils.CoinFactory;
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

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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

        String url = BASE_ENDPOINT + "/spot/v3/public/symbols";

        ResponseEntity<BybitSymbolData> responseEntity = restTemplate.getForEntity(url, BybitSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bybit, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bybit, код: " + statusCode);
        }

        return responseEntity.getBody()
                .getResult().getList()
                .stream().filter(symbol -> symbol.getShowStatus().equals("1") && symbol.getQuoteCoin().equals("USDT"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCoin()))
                .collect(Collectors.toSet());
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
        String sign = ApiExchangeUtils.generateBybitSignature(stringToSign, secret);

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
        String sign = ApiExchangeUtils.generateBybitSignature(stringToSign, secret);

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
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }

    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin + "USDT").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/spot/v3/public/quote/depth")
                                .queryParam("symbol", coin)
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
                        .bodyToFlux(String.class)
                        .map(response -> {
                            try {
                                BybitCoinDepth bybitCoinDepth = objectMapper.readValue(response, BybitCoinDepth.class);
                                bybitCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getBybitCoinDepth(bybitCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }
}
