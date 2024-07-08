package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.binance.chains.BinanceChainResponse;
import com.exchange.scanner.dto.response.exchangedata.binance.chains.BinanceNetwork;
import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.binance.tickervolume.BinanceCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.binance.coins.BinanceCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.binance.tradingfee.BinanceTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.Binance.BinanceCoinDepthBuilder;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.AppUtils.ListUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import com.exchange.scanner.services.utils.Binance.BinanceSignatureBuilder;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
public class ApiBinance implements ApiExchange {

    @Value("${exchanges.apiKeys.Binance.key}")
    private String key;

    @Value("${exchanges.apiKeys.Binance.secret}")
    private String secret;

    private static final String NAME = "Binance";

    private static final String BASE_ENDPOINT = "https://api.binance.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBinance() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {
        Set<Coin> coins = new HashSet<>();
        BinanceCurrencyResponse response = getCurrencies().block();
        if (response == null) return coins;

        coins = response.getSymbols().stream()
                .filter(symbol -> symbol.getQuoteAsset().equals("USDT") &&
                        symbol.getStatus().equals("TRADING") &&
                        symbol.getIsSpotTradingAllowed()
                )
                .map(symbol -> ObjectUtils.getCoin(symbol.getBaseAsset()))
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BinanceCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/exchangeInfo")
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
            .bodyToMono(BinanceCurrencyResponse.class)
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

        List<BinanceChainResponse> response = getChains().collectList().block();

        if (response == null) return chainsDTOSet;

        coins.forEach(coin -> {
            response.forEach(data -> {
                if (coin.getName().equals(data.getCoin())) {
                    Set<Chain> chains = new HashSet<>();
                    List<BinanceNetwork> networks = data.getNetworkList().stream()
                            .filter(network -> network.getDepositEnable() && network.getWithdrawEnable())
                            .toList();

                    networks.forEach(network -> {
                        Chain chain = new Chain();
                        chain.setName(coin.getName());
                        chain.setCommission(new BigDecimal(network.getWithdrawFee()));
                        chains.add(chain);
                    });
                    ChainResponseDTO chainResponseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                    chainsDTOSet.add(chainResponseDTO);
                }
            });
        });

        return chainsDTOSet;
    }

    private Flux<BinanceChainResponse> getChains() {
        Map<String, String> params = new HashMap<>();
        BinanceSignatureBuilder signatureBuilder = new BinanceSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path("/sapi/v1/capital/config/getall");
                signatureBuilder.getParameters().forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .headers(httpHeaders -> {
                signatureBuilder.getHeaders().forEach(httpHeaders::add);
            })
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(BinanceChainResponse.class)
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
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            List <BinanceTradingFeeResponse> response = getFee(coin).collectList().block();

            if (response != null) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        response.getFirst().getTakerCommission()
                );
                tradingFeeSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        return tradingFeeSet;
    }

    private Flux<BinanceTradingFeeResponse> getFee(Coin coin) {
        String symbol = coin.getSymbol() + "USDT";
        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        BinanceSignatureBuilder signatureBuilder = new BinanceSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path("/sapi/v1/asset/tradeFee");
                signatureBuilder.getParameters().forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .headers(httpHeaders -> {
                signatureBuilder.getHeaders().forEach(httpHeaders::add);
            })
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(BinanceTradingFeeResponse.class)
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
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        List<BinanceCoinTickerVolume> response = getCoinTickerVolume(new ArrayList<>(coins)).collectList().block();

        if (response == null) return volume24HSet;

        coins.forEach(coin -> {
            response.forEach(tradingFeeResponse -> {
                if (coin.getName().equals(tradingFeeResponse.getSymbol().replaceAll("USDT", ""))) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            tradingFeeResponse.getQuoteVolume()
                    );

                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Flux<BinanceCoinTickerVolume> getCoinTickerVolume(List<Coin> coins) {
        int maxSymbolPerRequest = 100;
        List<List<Coin>> partitions = ListUtils.partition(coins, maxSymbolPerRequest);
        return Flux.fromIterable(partitions)
            .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
            .flatMap(partition -> webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v3/ticker/24hr")
                        .queryParam("symbols", generateParameters(partition))
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
                .bodyToFlux(BinanceCoinTickerVolume.class)
                .onErrorResume(error -> {
                    if (error instanceof ReadTimeoutException) {
                        log.error("Превышен лимит ожидания ответа от {}.", NAME, error);
                    } else {
                        log.error("Ошибка при запросе к {}.", NAME, error);
                    }
                    return Flux.empty();
                })
            );
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            BinanceCoinDepth response = getCoinDepth(coin).block();

            if (response != null) {
                CoinDepth coinDepth = BinanceCoinDepthBuilder.getCoinDepth(coin, response);
                coinDepthSet.add(coinDepth);
            }
        });

        return coinDepthSet;
    }

    private Mono<BinanceCoinDepth> getCoinDepth(String coinName) {
        String symbol = coinName + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/depth")
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
            .bodyToMono(BinanceCoinDepth.class)
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
        sb.append("[");
        coins.forEach(coin -> sb.append("\"").append(coin.getName()).append("USDT").append("\"").append(","));
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        parameters = sb.toString();

        return parameters;
    }
}
