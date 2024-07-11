package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bitmart.chains.BitmartChainsCurrencies;
import com.exchange.scanner.dto.response.exchangedata.bitmart.chains.BitmartChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.bitmart.depth.BitmartCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitmart.coins.BitmartCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bitmart.tickervolume.BitmartVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.bitmart.tradingfee.BitmartTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.Bitmart.BitmartCoinDepthBuilder;
import com.exchange.scanner.services.utils.Bitmart.BitmartSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBitmart implements ApiExchange {

    @Value("${exchanges.apiKeys.Bitmart.key}")
    private String key;

    @Value("${exchanges.apiKeys.Bitmart.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.Bitmart.memo}")
    private String memo;

    private static final String NAME = "Bitmart";

    public final static String BASE_ENDPOINT = "https://api-cloud.bitmart.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int REQUEST_FEE_DELAY_DURATION = 1000;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private final WebClient webClient;

    public ApiBitmart() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        BitmartCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().getSymbols().stream()
                .filter(symbol -> symbol.getQuoteCurrency().equals("USDT") && symbol.getTradeStatus().equals("trading"))
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCurrency().toUpperCase() + "_USDT");
                    return ObjectUtils.getCoin(symbol.getBaseCurrency(), NAME, links);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BitmartCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v1/symbols/details")
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
            .bodyToMono(BitmartCurrencyResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        BitmartChainsResponse response = getChains().block();

        if (response == null || response.getData() == null) return chainsDTOSet;
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());
        List<BitmartChainsCurrencies> chainsCurrencies = response.getData().getCurrencies().stream()
                .filter(chainResponse -> coinsNames.contains(chainResponse.getCurrency()))
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainsCurrencies.forEach(responseChain -> {
                if (coin.getName().equals(responseChain.getCurrency())) {
                    Chain chain = new Chain();
                    chain.setName(responseChain.getCurrency());
                    chain.setCommission(new BigDecimal(responseChain.getWithdrawMinFee()));
                    chains.add(chain);
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<BitmartChainsResponse> getChains() {
        String requestPath = "/account/v1/currencies";
        BitmartSignatureBuilder signatureBuilder = new BitmartSignatureBuilder(secret, memo);
        signatureBuilder.createSignature("GET");

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .build()
            )
            .header("X-BM-TIMESTAMP", signatureBuilder.getTimestamp())
            .header("X-BM-KEY", key)
            .header("X-BM-SIGN", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitmartChainsResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            BitmartTradingFeeResponse response = getFee(coin).block();

            if (response != null && response.getData() != null) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        response.getData().getTakerFee()
                );
                tradingFeeSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_FEE_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return tradingFeeSet;
    }

    private Mono<BitmartTradingFeeResponse> getFee(Coin coin) {
        String requestPath = "/spot/v1/trade_fee";
        String symbol = coin.getName() + "_USDT";
        BitmartSignatureBuilder signatureBuilder = new BitmartSignatureBuilder(secret, memo);
        signatureBuilder.createSignature("GET", Collections.singletonMap("symbol", symbol));

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .queryParam("symbol", symbol)
                    .build()
            )
            .header("X-BM-TIMESTAMP", signatureBuilder.getTimestamp())
            .header("X-BM-KEY", key)
            .header("X-BM-SIGN", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitmartTradingFeeResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            BitmartVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null && response.getData() != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getData().getQv24h()
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

    public Mono<BitmartVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/quotation/v3/ticker")
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
            .bodyToMono(BitmartVolumeTicker.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            BitmartCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = BitmartCoinDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
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

    private Mono<BitmartCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/spot/quotation/v3/books")
                    .queryParam("symbol", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
                    .build())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitmartCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
