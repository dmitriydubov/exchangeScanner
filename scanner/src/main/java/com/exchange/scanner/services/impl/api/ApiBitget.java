package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bitget.chains.BitgetChainResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.depth.BitgetCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitget.exchangeinfo.BitgetSymbolData;
import com.exchange.scanner.dto.response.exchangedata.bitget.tickervolume.BitgetTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee.BitgetTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee.Data;
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
public class ApiBitget implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Bitget";

    public final static String BASE_ENDPOINT = "https://api.bitget.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBitget() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        String url = BASE_ENDPOINT + "/api/v2/spot/public/symbols";

        ResponseEntity<BitgetSymbolData> responseEntity = restTemplate.getForEntity(url, BitgetSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bitget, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bitget, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getQuoteCoin().equals("USDT") && symbol.getStatus().equals("online"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCoin()))
                .collect(Collectors.toSet());
    }

    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();
        coins.forEach(coin -> {
            List<Chain> response = getChain(coin).block();
            if (response != null) {
                Set<Chain> chains = new HashSet<>(response);
                coin.setChains(chains);
                coinsWithChains.add(coin);
            } else {
                log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            }
            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        return coinsWithChains;
    }

    private Mono<List<Chain>> getChain(Coin coin) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/spot/public/coins")
                        .queryParam("coin", coin.getName())
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
                .bodyToMono(BitgetChainResponse.class)
                .map(data -> data.getData().getFirst().getChains().stream()
                        .filter(chainDto -> chainDto.getWithdrawable().equals("true") && chainDto.getRechargeable().equals("true"))
                        .map(filteredChainsDto -> {
                            Chain chain = new Chain();
                            chain.setName(filteredChainsDto.getChain());
                            chain.setCommission(new BigDecimal(filteredChainsDto.getWithdrawFee()));
                            return chain;
                        })
                        .toList()
                );
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithFee = new HashSet<>();

        coins.forEach(coin -> {
            Data response = getFee(coin).block();

            if (response != null) {
                coin.setTakerFee(new BigDecimal(response.getTakerFeeRate()));
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

    private Mono <Data> getFee(Coin coin) {
        String symbol = coin.getName() + "USDT";
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/spot/public/symbols")
                        .queryParam("symbol", symbol)
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
                .bodyToMono(BitgetTradingFeeResponse.class)
                .map(response -> response.getData().getFirst());
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            BitgetTickerVolume response = getCoinTickerVolume(coin).block();

            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getData().getFirst().getQuoteVolume()));
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

    private Mono<BitgetTickerVolume> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getSymbol() + "USDT";

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/spot/market/tickers")
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
                .bodyToMono(BitgetTickerVolume.class);
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
                        .uri(uriBuilder -> uriBuilder.path("/api/v2/spot/market/merge-depth")
                                .queryParam("symbol", coin)
                                .queryParam("limit", DEPTH_REQUEST_LIMIT)
                                .build()
                        )
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                    log.error("Ошибка получения информации от " + NAME + ". Причина: {}", errorBody);
                                    return Mono.empty();
                                })
                        )
                        .bodyToFlux(String.class)
                        .map(response -> {
                            try {
                                BitgetCoinDepth bitgetCoinDepth = objectMapper.readValue(response, BitgetCoinDepth.class);
                                bitgetCoinDepth.setCoinName(coin.replaceAll("USDT", ""));

                                return ApiExchangeUtils.getBitgetCoinDepth(bitgetCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }
}
