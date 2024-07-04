package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.mexc.chains.MexcChainResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.depth.MexcCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee.MexcTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.exchangeinfo.MexcSymbolData;
import com.exchange.scanner.dto.response.exchangedata.mexc.tickervolume.MexcCoinTicker;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiMEXC implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${exchanges.apiKeys.MEXC.key}")
    private String key;

    @Value("${exchanges.apiKeys.MEXC.secret}")
    private String secret;

    private static final String NAME = "MEXC";

    public final static String BASE_ENDPOINT = "https://api.mexc.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiMEXC() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/api/v3/exchangeInfo";

        ResponseEntity<MexcSymbolData> responseEntity = restTemplate.getForEntity(url, MexcSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от MEXC, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от MEXC, код: " + statusCode);
        }

        return responseEntity.getBody().getSymbols().stream()
                .filter(symbol -> symbol.getStatus().equals("ENABLED") && symbol.getQuoteAsset().equals("USDT"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseAsset()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<MexcChainResponse> response = getChainResponse().block();

        if (response == null) {
            log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            return coinsWithChains;
        }
        Set<MexcChainResponse> filteredData = response.stream()
                .filter(data -> coinsNames.contains(data.getCoin()))
                .collect(Collectors.toSet());

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            filteredData.forEach(data -> {
                if (coin.getName().equals(data.getCoin())) {
                    data.getNetworkList().stream()
                        .filter(networkList -> networkList.getDepositEnable() && networkList.getWithdrawEnable())
                        .forEach(networkList -> {
                            Chain chain = new Chain();
                            chain.setName(networkList.getNetWork());
                            chain.setCommission(new BigDecimal(networkList.getWithdrawFee()));
                            chains.add(chain);
                        });
                }
            });
            coin.setChains(chains);
            coinsWithChains.add(coin);
        });

        return coinsWithChains;
    }

    private Mono<List<MexcChainResponse>> getChainResponse() {
        Map<String, String> params = new HashMap<>();
        String signature = ApiExchangeUtils.generateMexcSignature(params, secret);
        params.put("signature", signature);

        return webClient
                .get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v3/capital/config/getall");
                    params.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .header("X-MEXC-APIKEY", key)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(MexcChainResponse[].class)
                .map(Arrays::asList);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithFee = new HashSet<>();

        coins.forEach(coin -> {
            MexcTradingFeeResponse response = getFee(coin).block();
            if (response != null) {
                coin.setTakerFee(new BigDecimal(response.getData().getTakerCommission()));
                coinsWithFee.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithFee;
    }

    private Mono<MexcTradingFeeResponse> getFee(Coin coin) {
        Map<String, String> params = new HashMap<>();
        params.put("symbol", coin.getSymbol() + "USDT");
        String signature = ApiExchangeUtils.generateMexcSignature(params, secret);
        params.put("signature", signature);
        return webClient
                .get()
                .uri(uriBuilder -> {
                    uriBuilder.path("api/v3/tradeFee");
                    params.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .header("X-MEXC-APIKEY", key)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(MexcTradingFeeResponse.class);
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            MexcCoinTicker response = getCoinTickerVolume(coin).block();
            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getQuoteVolume()));
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

    private Mono<MexcCoinTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/ticker/24hr")
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
                .bodyToMono(MexcCoinTicker.class);
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
                        .uri(uriBuilder -> uriBuilder.path("/api/v3/depth")
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
                                MexcCoinDepth mexcCoinDepth = objectMapper.readValue(response, MexcCoinDepth.class);
                                mexcCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getMexcCoinDepth(mexcCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getSymbol()).append("USDT").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
