package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.coinw.chains.CoinWChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.coins.CoinWCoinAvailableResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWCoinDepthResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume.CoinWVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume.CoinWVolumeTickerData;
import com.exchange.scanner.dto.response.exchangedata.coinw.tradingfee.CoinWTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.CoinChainUtils;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.CoinW.CoinWCoinDepthBuilder;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
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
public class ApiCoinW implements ApiExchange {

    private static final String NAME = "CoinW";

    public final static String BASE_ENDPOINT = "https://www.coinw.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiCoinW() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        CoinWCoinAvailableResponse response = getCurrencies().block();
        if (response == null || response.getData() == null) return coins;
        coins = response.getData().stream()
                .filter(currency -> currency.getCurrencyQuote().equals("USDT") && currency.getState() == 1)
                .map(currency -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + currency.getCurrencyBase().toUpperCase() + "USDT");
                    return ObjectUtils.getCoin(currency.getCurrencyBase(), NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<CoinWCoinAvailableResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnSymbol")
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
            .bodyToMono(CoinWCoinAvailableResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        CoinWChainsResponse response = getChains().block();
        if (response == null || response.getData() == null) return chainsDTOSet;

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            response.getData().forEach((symbol, chainResponse) -> {
                if (coin.getName().equals(symbol) &&
                        !chainResponse.getChain().equals("1") &&
                        chainResponse.getRecharge().equals("1") &&
                        chainResponse.getWithDraw().equals("1")
                ) {
                    String chainName = chainResponse.getChain();
                    if (chainName.endsWith("@BSC")) {
                        chainName = chainName.substring(0, chainName.length() - "@BSC".length());
                    }
                    Chain chain = new Chain();
                    chain.setName(CoinChainUtils.unifyChainName(chainName.toUpperCase()));
                    chain.setCommission(new BigDecimal(chainResponse.getTxFee()));
                    chain.setMinConfirm(0);
                    chains.add(chain);
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<CoinWChainsResponse> getChains() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnCurrencies")
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
            .bodyToMono(CoinWChainsResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        CoinWTradingFeeResponse response = getFee().block();
        if (response == null || response.getData() == null) return tradingFeeSet;
        coins.forEach(coin -> response.getData().forEach((key, value) -> {
            if (coin.getName().equals(key)) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        value.getTxFee()
                );
                tradingFeeSet.add(responseDTO);
            }
        }));

        return tradingFeeSet;
    }

    private Mono<CoinWTradingFeeResponse> getFee() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/appApi.html")
                    .queryParam("action", "currencys")
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinWTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        CoinWVolumeResponse response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "_USDT").toList();
        Map<String, CoinWVolumeTickerData> data = response.getData().entrySet().stream()
                .filter(entry -> symbols.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        coins.forEach(coin -> data.forEach((coinName, volume) -> {
            if (coinName.equalsIgnoreCase(coin.getName() + "_USDT")) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        volume.getBaseVolume()
                );
                volume24HSet.add(responseDTO);
            }
        }));

        return volume24HSet;
    }

    private Mono<CoinWVolumeResponse> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnTicker")
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
            .bodyToMono(CoinWVolumeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet= new HashSet<>();

        coins.forEach(coin -> {
            CoinWCoinDepthResponse response = getDepthResponse(coin).block();
            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = CoinWCoinDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
                coinDepthSet.add(coinDepth);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

    }

    private Mono<CoinWCoinDepthResponse> getDepthResponse(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/public")
                    .queryParam("command", "returnOrderBook")
                    .queryParam("symbol", symbol)
                    .queryParam("size", DEPTH_REQUEST_LIMIT)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения стакана цен для символа {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(CoinWCoinDepthResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
