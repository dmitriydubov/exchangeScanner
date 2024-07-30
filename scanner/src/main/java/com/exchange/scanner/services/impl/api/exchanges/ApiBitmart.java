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
import com.exchange.scanner.services.utils.AppUtils.CoinChainUtils;
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

    private static final int REQUEST_DELAY_DURATION = 400;

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
                    return ObjectUtils.getCoin(symbol.getBaseCurrency(), NAME, links, false);
                })
                .collect(Collectors.toSet());

        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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
                .filter(chainResponse -> coinsNames.contains(chainResponse.getCurrency()) &&
                        chainResponse.getDepositEnabled() &&
                        chainResponse.getWithdrawEnabled()
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainsCurrencies.forEach(responseChain -> {
                if (coin.getName().equals(responseChain.getCurrency())) {
                    String chainName = CoinChainUtils.unifyChainName(responseChain.getNetwork().toUpperCase());
                    Chain chain = new Chain();
                    chain.setName(chainName);
                    chain.setCommission(new BigDecimal(responseChain.getWithdrawMinFee()));
                    chain.setMinConfirm(0);
                    chains.add(chain);
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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
        BitmartTradingFeeResponse response = getFee().block();
        if (response == null || response.getData() == null) {
            return tradingFeeSet;
        }
        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    response.getData().getTakerFee()
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    private Mono<BitmartTradingFeeResponse> getFee() {
        String requestPath = "/spot/v1/user_fee";
        BitmartSignatureBuilder signatureBuilder = new BitmartSignatureBuilder(secret, memo);
        signatureBuilder.createSignature("GET", new HashMap<>());

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
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
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
        BitmartVolumeTicker response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "_USDT").toList();
        List<List<String>> volumeData = response.getData().stream()
                .filter(data -> symbols.contains(data.getFirst()))
                .toList();

        coins.forEach(coin -> {
            volumeData.forEach(data -> {
                if (data.getFirst().equalsIgnoreCase(coin.getName().toUpperCase() + "_USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.get(3)
                    );

                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    public Mono<BitmartVolumeTicker> getCoinTickerVolume() {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/quotation/v3/tickers")
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
