package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bingx.chains.BingXChainResponse;
import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume.BingXVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.bingx.coins.BingXCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bingx.tradingfee.BingXTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.BingX.BingXCoinDepthBuilder;
import com.exchange.scanner.services.utils.BingX.BingXSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.CoinFactory;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();

        BingXCurrencyResponse response = getCurrencies().block();

        if (response == null) return coins;

        coins = response.getData().getSymbols().stream()
                .filter(symbol -> symbol.getSymbol().endsWith("-USDT") && symbol.getStatus() == 1)
                .map(symbol -> {
                    String coinName = CoinFactory.refactorToStandardCoinName(symbol.getSymbol(), "-");
                    return CoinFactory.getCoin(coinName);
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
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();

        coins.forEach(coin -> {
            BingXChainResponse response = getChains(coin).block();

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                Set<Chain> chains = new HashSet<>();

                response.getData().getFirst().getNetworkList().forEach(network -> {
                    Chain chain = new Chain();
                    chain.setName(network.getNetwork());
                    chain.setCommission(new BigDecimal(network.getWithdrawFee()));
                    chains.add(chain);
                });

                coin.setChains(chains);
                coinsWithChains.add(coin);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        return coinsWithChains;
    }

    private Mono<BingXChainResponse> getChains(Coin coin) {
        String symbol = coin.getName();
        String requestPath = "/openApi/wallets/v1/capital/config/getall";
        TreeMap<String, String> params = new TreeMap<>();
        params.put("coin", symbol);
        BingXSignatureBuilder signatureBuilder = new BingXSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                signatureBuilder.getParameters().forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .headers(httpHeaders -> {
                signatureBuilder.getHeaders().forEach(httpHeaders::add);
            })
            .header("signature", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BingXChainResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithTradingFee = new HashSet<>();

        coins.forEach(coin -> {
            BingXTradingFeeResponse response = getFee(coin).block();

            if (response != null) {
                coin.setTakerFee(new BigDecimal(response.getData().getTakerCommissionRate()));
                coinsWithTradingFee.add(coin);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithTradingFee;
    }

    private Mono<BingXTradingFeeResponse> getFee(Coin coin) {
        String symbol = coin.getName() + "-USDT";
        String requestPath = "/openApi/spot/v1/user/commissionRate";
        TreeMap<String, String> params = new TreeMap<>();
        params.put("symbol", symbol);
        BingXSignatureBuilder signatureBuilder = new BingXSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                signatureBuilder.getParameters().forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .headers(httpHeaders -> {
                signatureBuilder.getHeaders().forEach(httpHeaders::add);
            })
            .header("signature", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(String.class)
            .handle((response, sink) -> {
                try {
                    sink.next(objectMapper.readValue(response, BingXTradingFeeResponse.class));
                } catch (IOException ex) {
                    log.error("Ошибка десериализации ответа при получении торговой комиссии от BingX", ex);
                    sink.error(new RuntimeException());
                }
            });
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            BingXVolumeTicker response = getCoinTickerVolume(coin).block();

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

    private Mono<BingXVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "-USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/openApi/spot/v1/ticker/24hr")
                    .queryParam("symbol", symbol)
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
            .bodyToMono(BingXVolumeTicker.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepths = new HashSet<>();

        coins.forEach(coin -> {
            BingXCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = BingXCoinDepthBuilder.getCoinDepth(coin, response.getData());
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

    private Mono<BingXCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "_USDT";

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
            .bodyToMono(BingXCoinDepth.class);
    }
}
