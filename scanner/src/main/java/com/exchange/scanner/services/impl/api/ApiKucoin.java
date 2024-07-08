package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.kucoin.chains.KucoinChainResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tradingfee.KucoinTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.coins.KucoinCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume.KucoinTickerVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.Kucoin.KucoinCoinDepthBuilder;
import com.exchange.scanner.services.utils.Kucoin.KucoinSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.ListUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import io.netty.handler.timeout.ReadTimeoutException;
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
public class ApiKucoin implements ApiExchange {

    @Value("${exchanges.apiKeys.Kucoin.key}")
    private String key;

    @Value("${exchanges.apiKeys.Kucoin.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.Kucoin.passphrase}")
    private String passphrase;

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
        Set<Coin> coins = new HashSet<>();
        KucoinCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getData().stream()
                .filter(currency -> currency.getQuoteCurrency().equals("USDT") && currency.getEnableTrading())
                .map(currency -> ObjectUtils.getCoin(currency.getBaseCurrency()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<KucoinCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/symbols")
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
            .bodyToMono(KucoinCurrencyResponse.class)
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
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        coins.forEach(coin -> {
            List<List<Chain>> response = getChain(coin).collectList().block();
            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.forEach(chains::addAll);
                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            } else {
                log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            }
        });

        return chainsDTOSet;
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
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Flux.empty();
            })
            .map(response -> response.getData().getChains().stream()
                    .filter(chainDto -> chainDto.getIsDepositEnabled() && chainDto.getIsWithdrawEnabled())
                    .map(dto -> {
                       String chainName = dto.getChainName();
                       if (dto.getChainName().equals("Lightning Network")) {
                           chainName = "LIGHTNING";
                       }
                       Chain chain = new Chain();
                       chain.setName(chainName);
                       chain.setCommission(new BigDecimal(dto.getWithdrawalMinFee()));
                       return chain;
                    })
                    .toList()
            );
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        KucoinTradingFeeResponse response = getFee(coins).blockLast();

        if (response == null) return tradingFeeSet;

        coins.forEach(coin -> {
            response.getData().forEach(responseFee -> {
                if (coin.getName().equals(responseFee.getSymbol().replaceAll("-USDT", ""))) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            responseFee.getTakerFeeRate()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
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
                String signature = KucoinSignatureBuilder.generateKucoinSignature(secret, strToSign);
                String encodedPassphrase = KucoinSignatureBuilder.generateKucoinPassphrase(secret, passphrase);
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
                    .bodyToFlux(KucoinTradingFeeResponse.class)
                    .onErrorResume(error -> {
                        if (error instanceof ReadTimeoutException) {
                            log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                        } else {
                            log.error("Ошибка при запросе к {}.", NAME, error);
                        }
                        return Flux.empty();
                    });
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            KucoinTickerVolumeResponse response = getCoinTickerVolume(coin).block();
            if (response != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getData().getVolValue()
                );

                volume24HSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw  new RuntimeException();
            }
        });

        return volume24HSet;
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
            .bodyToMono(KucoinTickerVolumeResponse.class)
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
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            KucoinCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = KucoinCoinDepthBuilder.getCoinDepth(coin, response.getData());
                coinDepthSet.add(coinDepth);
            }

            try {
                Thread.sleep(DEPTH_REQUEST_LIMIT);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepthSet;
    }

    private Mono<KucoinCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "-USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/api/v1/market/orderbook/level2_" + DEPTH_REQUEST_LIMIT)
                    .queryParam("symbol", symbol)
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
            .bodyToMono(KucoinCoinDepth.class)
            .onErrorResume(error -> {
                if (error instanceof ReadTimeoutException) {
                    log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                } else {
                    log.error("Ошибка при запросе к {}.", NAME, error);
                }
                return Mono.empty();
            });
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
