package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.kucoin.chains.KucoinChainData;
import com.exchange.scanner.dto.response.exchangedata.kucoin.chains.KucoinChainResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume.KucoinTicker;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tradingfee.KucoinTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.coins.KucoinCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume.KucoinTickerVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Kucoin.KucoinCoinDepthBuilder;
import com.exchange.scanner.services.utils.Kucoin.KucoinSignatureBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();
        KucoinCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
                .filter(currency -> currency.getQuoteCurrency().equals("USDT") && currency.getEnableTrading())
                .map(currency -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + currency.getBaseCurrency().toUpperCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + currency.getBaseCurrency().toUpperCase());
                    links.setTradeLink(exchange.getTradeLink() + currency.getBaseCurrency().toUpperCase() + "-USDT");
                    return ObjectUtils.getCoin(currency.getBaseCurrency(), NAME, links, currency.getIsMarginEnabled());
                })
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        KucoinChainResponse response = getChain().block();
        if (response == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<KucoinChainData> chainData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCurrency()))
                .filter(data -> data.getChains().stream()
                        .allMatch(chain -> chain.getIsDepositEnabled() && chain.getIsWithdrawEnabled())
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            chainData.forEach(data -> {
                if (data.getCurrency().equalsIgnoreCase(coin.getName())) {
                    data.getChains().forEach(chainDTO -> {
                        String chainName = CoinChainUtils.unifyChainName(chainDTO.getChainName());
                        Chain chain = new Chain();
                        chain.setName(chainName);
                        chain.setCommission(new BigDecimal(chainDTO.getWithdrawalMinFee()));
                        chain.setMinConfirm(chainDTO.getConfirms());
                        chains.add(chain);
                    });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<KucoinChainResponse> getChain() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/currencies")
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
            .bodyToMono(KucoinChainResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        KucoinTradingFeeResponse response = getFee().blockLast();

        if (response == null || response.getData() == null) return tradingFeeSet;

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    response.getData().getTakerFeeRate()
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    private Flux<KucoinTradingFeeResponse> getFee() {
        String endpoint = "/api/v1/base-fee";

        String timestamp = String.valueOf(System.currentTimeMillis());
        String strToSign = timestamp + "GET" + endpoint;
        String signature = KucoinSignatureBuilder.generateKucoinSignature(secret, strToSign);
        String encodedPassphrase = KucoinSignatureBuilder.generateKucoinPassphrase(secret, passphrase);
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(endpoint)
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        KucoinTickerVolumeResponse response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "-USDT").toList();
        List<KucoinTicker> ticker = response.getData().getTicker().stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            ticker.forEach(data -> {
                if (data.getSymbol().equalsIgnoreCase(coin.getName() + "-USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getVolValue()
                    );

                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<KucoinTickerVolumeResponse> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/market/allTickers")
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            KucoinCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = KucoinCoinDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
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

    private Mono<KucoinCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "-USDT";

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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
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
