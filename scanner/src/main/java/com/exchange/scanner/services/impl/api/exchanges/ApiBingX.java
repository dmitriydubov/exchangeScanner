package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bingx.chains.BingXChainData;
import com.exchange.scanner.dto.response.exchangedata.bingx.chains.BingXChainResponse;
import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume.BingXVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.bingx.coins.BingXCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume.BingXVolumeTickerData;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.CoinChainUtils;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.BingX.BingXCoinDepthBuilder;
import com.exchange.scanner.services.utils.BingX.BingXSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiBingX implements ApiExchange {

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${exchanges.apiKeys.BingX.key}")
    private String key;

    @Value("${exchanges.apiKeys.BingX.secret}")
    private String secret;

    private static final String NAME = "BingX";

    public final static String BASE_ENDPOINT = "https://open-api.bingx.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final String TYPE_REQUEST = "step0";

    private final WebClient webClient;

    public ApiBingX() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        BingXCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().getSymbols().stream()
                .filter(symbol -> symbol.getSymbol().endsWith("-USDT") && symbol.getStatus() == 1)
                .map(symbol -> {
                    String coinName = ObjectUtils.refactorToStandardCoinName(symbol.getSymbol(), "-");
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + coinName.toUpperCase() + "USDT");
                    return ObjectUtils.getCoin(coinName, NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BingXCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/openApi/spot/v1/common/symbols")
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
            .bodyToMono(String.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            })
            .handle((response, sink) -> {
                try {
                    sink.next(objectMapper.readValue(response, BingXCurrencyResponse.class));
                } catch (IOException ex) {
                    log.error("Ошибка десериализации ответа при получении списка монет от BingX", ex);
                    sink.error(new RuntimeException());
                }
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        BingXChainResponse response = getChains().block();
        if (response == null || response.getData() == null) return chainsDTOSet;
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());
        List<BingXChainData> okxChainData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCoin()))
                .filter(data -> data.getNetworkList().stream()
                        .allMatch(network -> network.getDepositEnable() && network.getWithdrawEnable()))
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            okxChainData.forEach(chainData -> {
                if (chainData.getCoin().equals(coin.getName())) {
                    chainData.getNetworkList().forEach(network -> {
                        String chainName = CoinChainUtils.unifyChainName(network.getNetwork());
                        Chain chain = new Chain();
                        chain.setName(chainName.toUpperCase());
                        chain.setCommission(new BigDecimal(network.getWithdrawFee()));
                        chain.setMinConfirm(network.getMinConfirm());
                        chains.add(chain);
                    });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<BingXChainResponse> getChains() {
        String requestPath = "/openApi/wallets/v1/capital/config/getall";
        TreeMap<String, String> params = new TreeMap<>();
        BingXSignatureBuilder signatureBuilder = new BingXSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                signatureBuilder.getParameters().forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .headers(httpHeaders -> signatureBuilder.getHeaders().forEach(httpHeaders::add))
            .header("signature", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BingXChainResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    "0.001"
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        BingXVolumeTicker response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "-USDT").toList();
        List<BingXVolumeTickerData> volumeData = response.getData().stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> volumeData.forEach(data -> {
            if (data.getSymbol().equalsIgnoreCase(coin.getName() + "-USDT")) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        data.getQuoteVolume()
                );
                volume24HSet.add(responseDTO);
            }
        }));

        return volume24HSet;
    }

    private Mono<BingXVolumeTicker> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/openApi/spot/v1/ticker/24hr")
                    .queryParam("timestamp", new Timestamp(System.currentTimeMillis()).getTime())
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
            .bodyToMono(BingXVolumeTicker.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepths = new HashSet<>();

        coins.forEach(coin -> {
            BingXCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = BingXCoinDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
                coinDepths.add(coinDepth);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepths;
    }

    private Mono<BingXCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/openApi/spot/v2/market/depth")
                    .queryParam("symbol", symbol)
                    .queryParam("depth", DEPTH_REQUEST_LIMIT)
                    .queryParam("type", TYPE_REQUEST)
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
            .bodyToMono(BingXCoinDepth.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
