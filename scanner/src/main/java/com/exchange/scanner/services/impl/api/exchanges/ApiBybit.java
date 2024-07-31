package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bybit.chains.BybitChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.chains.BybitChainsRows;
import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.coins.BybitCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.tickervolume.BybitCoinTickerList;
import com.exchange.scanner.dto.response.exchangedata.bybit.tickervolume.BybitCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee.BybitTradingFeeList;
import com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee.BybitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.CoinChainUtils;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.Bybit.BybitCoinDepthBuilder;
import com.exchange.scanner.services.utils.Bybit.BybitSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        BybitCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getResult() == null) return coins;

        coins = response.getResult().getList().stream()
                .filter(symbol -> symbol.getShowStatus().equals("1") && symbol.getQuoteCoin().equals("USDT"))
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCoin().toUpperCase() + "/USDT/");
                    return ObjectUtils.getCoin(symbol.getBaseCoin(), NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BybitCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v3/public/symbols")
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
            .bodyToMono(BybitCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        BybitChainsResponse response = getChains().block();
        if (response == null || response.getResult() == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<BybitChainsRows> chainsRows = response.getResult().getRows().stream()
                .filter(row -> coinsNames.contains(row.getCoin()))
                .filter(row -> row.getChains().stream()
                        .allMatch(chain -> chain.getChainDeposit().equals("1") && chain.getChainWithdraw().equals("1"))
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            chainsRows.forEach(chainRow -> {
                if (chainRow.getCoin().equalsIgnoreCase(coin.getName())) {
                    chainRow.getChains().forEach(chainResponse -> {
                        String chainName = CoinChainUtils.unifyChainName(chainResponse.getChain().toUpperCase());
                        Chain chain = new Chain();
                        chain.setName(chainName);
                        chain.setCommission(new BigDecimal(chainResponse.getWithdrawFee()));
                        chain.setMinConfirm(Integer.valueOf(chainResponse.getConfirmation()));
                        chains.add(chain);
                    });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<BybitChainsResponse> getChains() {
        String timestamp = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
        String recv = "5000";
        String stringToSign = timestamp + key + recv;
        String sign = BybitSignatureBuilder.generateBybitSignature(stringToSign, secret);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v5/asset/coin/query-info")
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
            .bodyToMono(BybitChainsResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        BybitTradingFeeResponse response = getFee().block();
        if (response == null || response.getResult() == null) return tradingFeeSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<BybitTradingFeeList> tradingFeeList = response.getResult().getList().stream()
                .filter(result -> symbols.contains(result.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            tradingFeeList.forEach(result -> {
                if (result.getSymbol().equalsIgnoreCase(coin.getName() + "USDT")) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            result.getTakerFeeRate()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Mono<BybitTradingFeeResponse> getFee() {
        String timestamp = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
        String recv = "5000";
        String paramStr = "category=spot";
        String stringToSign = timestamp + key + recv + paramStr;
        String sign = BybitSignatureBuilder.generateBybitSignature(stringToSign, secret);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v5/account/fee-rate")
                    .queryParam("category", "spot")
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
                        log.error("Ошибка получения торговой комиссии от " + NAME + ".Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BybitTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        BybitCoinTickerVolume response = getCoinTickerVolume().block();
        if (response == null || response.getResult() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<BybitCoinTickerList> tickerList = response.getResult().getList().stream()
                .filter(data -> symbols.contains(data.getS()))
                .toList();

        coins.forEach(coin -> {
            tickerList.forEach(data -> {
                if (data.getS().equalsIgnoreCase(coin.getName() + "USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getQv()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<BybitCoinTickerVolume> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v3/public/quote/ticker/24hr")
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
            .bodyToMono(BybitCoinTickerVolume.class)
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
            BybitCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getResult() != null) {
                CoinDepth coinDepth = BybitCoinDepthBuilder.getCoinDepth(coin, response.getResult(), exchange);
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

    private Mono<BybitCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/spot/v3/public/quote/depth")
                    .queryParam("symbol", symbol)
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
            .bodyToMono(BybitCoinDepth.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
