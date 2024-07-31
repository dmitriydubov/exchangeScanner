package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.okx.chains.OKXChainData;
import com.exchange.scanner.dto.response.exchangedata.okx.chains.OKXChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.okx.depth.OKXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.okx.coins.OKXCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.okx.tickervolume.OKXDataVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.okx.tickervolume.OKXVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.okx.tradingfee.OKXTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.OKX.OKXDepthBuilder;
import com.exchange.scanner.services.utils.OKX.OKXSignatureBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiOKX implements ApiExchange {

    @Value("${exchanges.apiKeys.OKX.key}")
    private String key;

    @Value("${exchanges.apiKeys.OKX.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.OKX.passphrase}")
    private String passphrase;

    private static final String NAME = "OKX";

    public final static String BASE_ENDPOINT = "https://www.okx.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiOKX() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        OKXCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQuoteCcy().equals("USDT") && symbol.getState().equals("live"))
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCcy().toLowerCase() + "-usdt");
                    return ObjectUtils.getCoin(symbol.getBaseCcy(), NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<OKXCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v5/public/instruments")
                    .queryParam("instType", "SPOT")
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
            .bodyToMono(OKXCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        OKXChainsResponse response = getChains().block();

        if (response == null || response.getData() == null) return chainsDTOSet;
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());
        List<OKXChainData> okxChainData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCcy()) && data.getCanWd() && data.getCanDep())
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            okxChainData.forEach(chainResponse -> {
               if (coin.getName().equals(chainResponse.getCcy())) {
                   String chainName = CoinChainUtils.unifyChainName(chainResponse.getChain().replaceAll("-.*", ""));
                   Chain chain = new Chain();
                   chain.setName(chainName);
                   chain.setCommission(new BigDecimal(chainResponse.getMaxFee()));
                   chain.setMinConfirm(Integer.valueOf(chainResponse.getMinWdUnlockConfirm()));
                   chains.add(chain);
               }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<OKXChainsResponse> getChains() {
        String requestPath = "/api/v5/asset/currencies";
        OKXSignatureBuilder signatureBuilder = new OKXSignatureBuilder(secret);
        signatureBuilder.createSignature("GET", requestPath, new HashMap<>());
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(requestPath)
                        .build()
                )
                .header("OK-ACCESS-KEY", key)
                .header("OK-ACCESS-SIGN", signatureBuilder.getSignature())
                .header("OK-ACCESS-TIMESTAMP", signatureBuilder.getTimestamp())
                .header("OK-ACCESS-PASSPHRASE", passphrase)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(OKXChainsResponse.class)
                .onErrorResume(error -> {
                    LogsUtils.createErrorResumeLogs(error, NAME);
                    return Mono.empty();
                });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        OKXTradingFeeResponse response = getFee().block();
        if (response == null || response.getData() == null) return tradingFeeSet;

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    String.valueOf(new BigDecimal(response.getData().getFirst().getTaker()).abs())
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    private Mono<OKXTradingFeeResponse> getFee() {
        String requestPath = "/api/v5/account/trade-fee";
        Map<String, String> params = new HashMap<>();
        params.put("instType", "SPOT");
        OKXSignatureBuilder signatureBuilder = new OKXSignatureBuilder(secret);
        signatureBuilder.createSignature("GET", requestPath, params);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .queryParam("instType", "SPOT")
                    .build()
            )
            .header("OK-ACCESS-KEY", key)
            .header("OK-ACCESS-SIGN", signatureBuilder.getSignature())
            .header("OK-ACCESS-TIMESTAMP", signatureBuilder.getTimestamp())
            .header("OK-ACCESS-PASSPHRASE", passphrase)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(OKXTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        OKXVolumeTicker response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "-USDT").toList();
        List<OKXDataVolumeTicker> dataVolume = response.getData().stream()
                .filter(data -> symbols.contains(data.getInstId()))
                .toList();

        coins.forEach(coin -> {
            dataVolume.forEach(data -> {
                if (data.getInstId().equalsIgnoreCase(coin.getName() + "-USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getVolCcy24h()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<OKXVolumeTicker> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v5/market/tickers")
                    .queryParam("instType", "SPOT")
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
            .bodyToMono(OKXVolumeTicker.class)
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
            OKXCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = OKXDepthBuilder.getCoinDepth(coin, response.getData().getFirst(), exchange);
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

    private Mono<OKXCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "-USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/api/v5/market/books")
                        .queryParam("instId", symbol)
                        .queryParam("sz", DEPTH_REQUEST_LIMIT)
                        .build())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(OKXCoinDepth.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    private static String generateSymbolsParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName()).append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }

}
