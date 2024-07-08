package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.poloniex.chains.PoloniexChain;
import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.poloniex.coins.PoloniexCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.poloniex.tickervolume.PoloniexVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.Poloniex.PoloniexCoinDepthBuilder;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;

import com.poloniex.api.client.model.OrderBook;
import com.poloniex.api.client.rest.PoloRestClient;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiPoloniex implements ApiExchange {

    @Value("${exchanges.apiKeys.Poloniex.key}")
    private String key;

    @Value("${exchanges.apiKeys.Poloniex.secret}")
    private String secret;

    private static final String NAME = "Poloniex";

    public final static String BASE_ENDPOINT = "https://api.poloniex.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiPoloniex() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        List<PoloniexCurrencyResponse> response = getCurrencies().collectList().block();

        if (response == null) return coins;

        coins = response.stream()
                .filter(symbol -> symbol.getQuoteCurrencyName().equals("USDT") && symbol.getState().equals("NORMAL"))
                .map(symbol -> ObjectUtils.getCoin(symbol.getBaseCurrencyName()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Flux<PoloniexCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/markets")
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
            .bodyToFlux(PoloniexCurrencyResponse.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Flux.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();

        coins.forEach(coin -> {
            Map<String, PoloniexChain> response = getChains(coin).block();

            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.forEach((coinKey, responseValue) -> {
                    Chain chain = new Chain();
                    chain.setName(responseValue.getBlockchain());
                    chain.setCommission(new BigDecimal(responseValue.getWithdrawalFee()));
                    chains.add(chain);
                });
                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return chainsDTOSet;
    }

    private Mono<Map<String, PoloniexChain>> getChains(Coin coin) {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/currencies/{currency}")
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
            .bodyToMono(new ParameterizedTypeReference<Map<String, PoloniexChain>>() {})
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
    }

    @Override
    public  Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        PoloRestClient poloniexApiClient = new PoloRestClient(BASE_ENDPOINT, key, secret);

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    poloniexApiClient.getFeeInfo().getTakerRate()
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            PoloniexVolumeTicker response = getCoinTicker(coin).block();
            if (response != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getAmount()
                );

                volume24HSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return volume24HSet;
    }

    private Mono<PoloniexVolumeTicker> getCoinTicker(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/markets/{symbol}/ticker24h")
                    .build(symbol)
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торгового объёма за 24 часа от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(PoloniexVolumeTicker.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        PoloRestClient poloRestClient = new PoloRestClient(BASE_ENDPOINT);
        Set<CoinDepth> coinDepths = new HashSet<>();
        coins.forEach(coin -> {
            OrderBook response = poloRestClient.getOrderBook(coin + "_USDT", "0.01", DEPTH_REQUEST_LIMIT);
            PoloniexCoinDepth poloniexCoinDepth = new PoloniexCoinDepth();
            poloniexCoinDepth.setCoinName(coin);
            poloniexCoinDepth.setAsks(response.getAsks());
            poloniexCoinDepth.setBids(response.getBids());
            CoinDepth coinDepth = PoloniexCoinDepthBuilder.getPoloniexCoinDepth(poloniexCoinDepth);
            coinDepths.add(coinDepth);
            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepths;
    }
}
