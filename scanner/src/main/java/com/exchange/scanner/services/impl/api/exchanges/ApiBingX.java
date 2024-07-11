package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bingx.chains.BingXChainResponse;
import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume.BingXVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.bingx.coins.BingXCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bingx.tradingfee.BingXTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
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
                    return ObjectUtils.getCoin(coinName, NAME, links);
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

                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        return chainsDTOSet;
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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            BingXTradingFeeResponse response = getFee(coin).block();

            if (response != null && response.getData() != null) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        response.getData().getTakerCommissionRate()
                );
                tradingFeeSet.add(responseDTO);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return tradingFeeSet;
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
            .headers(httpHeaders -> signatureBuilder.getHeaders().forEach(httpHeaders::add))
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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            })
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
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            BingXVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null && response.getData() != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getData().getFirst().getQuoteVolume()
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
            .bodyToMono(BingXVolumeTicker.class)
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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
