package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.binance.chains.BinanceChainResponse;
import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.binance.tickervolume.BinanceCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.binance.coins.BinanceCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.binance.tradingfee.BinanceTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Binance.BinanceCoinDepthBuilder;
import com.exchange.scanner.services.utils.Binance.BinanceSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ApiBinance implements ApiExchange {

    @Value("${exchanges.apiKeys.Binance.key}")
    private String key;

    @Value("${exchanges.apiKeys.Binance.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private final BlockingDeque<Runnable> taskQueue = new LinkedBlockingDeque<>(20);

    private final ExecutorService executorService = new ThreadPoolExecutor(
            10,
            20,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private static final String NAME = "Binance";

    private static final String BASE_HTTP_ENDPOINT = "https://api.binance.com";

    private static final int HTTP_REQUEST_TIMEOUT = 10000;

    private static final String WSS_URL = "wss://stream.binance.com:9443/ws";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(10);

    private final WebClient webClient;

    public ApiBinance() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_HTTP_ENDPOINT, HTTP_REQUEST_TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();
        BinanceCurrencyResponse response = getCurrencies().block();
        if (response == null || response.getSymbols() == null) return coins;

        coins = response.getSymbols().stream()
                .filter(symbol -> symbol.getQuoteAsset().equals("USDT") &&
                        symbol.getStatus().equals("TRADING") &&
                        symbol.getIsSpotTradingAllowed()
                )
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + symbol.getBaseAsset().toUpperCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + symbol.getBaseAsset().toUpperCase());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseAsset().toUpperCase() + "_USDT");
                    return ObjectUtils.getCoin(symbol.getBaseAsset(), NAME, links, symbol.getIsMarginTradingAllowed());
                })
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();

        List<BinanceChainResponse> response = getChains().collectList().block();

        if (response == null || response.isEmpty()) return chainsDTOSet;

        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<BinanceChainResponse> chainResponse = response.stream()
                .filter(binanceChainResponse -> coinsNames.contains(binanceChainResponse.getCoin().toUpperCase()))
                .filter(binanceChainResponse -> binanceChainResponse.getNetworkList().stream()
                        .allMatch(binanceNetwork -> binanceNetwork.getDepositEnable() && binanceNetwork.getWithdrawEnable()))
                .toList();

        coins.forEach(coin -> chainResponse.forEach(data -> {
            if (coin.getName().equals(data.getCoin())) {
                Set<Chain> chains = new HashSet<>();

                data.getNetworkList().forEach(network -> {
                    String chainName = CoinChainUtils.unifyChainName(network.getNetwork());
                    Chain chain = new Chain();
                    chain.setName(chainName);
                    chain.setCommission(new BigDecimal(network.getWithdrawFee()));
                    chain.setMinConfirm(network.getMinConfirm());
                    chains.add(chain);
                });
                ChainResponseDTO chainResponseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(chainResponseDTO);
            }
        }));

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
            .headers(httpHeaders -> signatureBuilder.getHeaders().forEach(httpHeaders::add))
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(BinanceChainResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        List<BinanceTradingFeeResponse> response = getFee().collectList().block();
        if (response == null || response.getFirst() == null) return tradingFeeSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<BinanceTradingFeeResponse> filteredResponse = response.stream()
                .filter(feeData -> symbols.contains(feeData.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            filteredResponse.forEach(feeData -> {
                if (feeData.getSymbol().equalsIgnoreCase(coin.getName() + "USDT")) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            feeData.getTakerCommission()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Flux<BinanceTradingFeeResponse> getFee() {
        Map<String, String> params = new HashMap<>();
        BinanceSignatureBuilder signatureBuilder = new BinanceSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path("/sapi/v1/asset/tradeFee");
                signatureBuilder.getParameters().forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .headers(httpHeaders -> signatureBuilder.getHeaders().forEach(httpHeaders::add))
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(BinanceTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        List<BinanceCoinTickerVolume> response = getCoinTickerVolume().collectList().block();
        if (response == null || response.isEmpty()) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<BinanceCoinTickerVolume> filteredResponse = response.stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> filteredResponse.forEach(tradingFeeResponse -> {
            if (tradingFeeResponse.getSymbol().equalsIgnoreCase(coin.getName() + "USDT")) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        tradingFeeResponse.getQuoteVolume()
                );

                volume24HSet.add(responseDTO);
            }
        }));

        return volume24HSet;
    }

    private Flux<BinanceCoinTickerVolume> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/ticker/24hr")
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "usdt").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(Coin::getName, coin -> coin));

        int batchSize = 100;
        List<List<String>> batches = ListUtils.partition(symbols, batchSize);

        batches.forEach(batch -> getCoinDepth(batch, coinMap));
    }

    private void getCoinDepth(List<String> symbols, Map<String, Coin> coinMap) {
        submitExecutorService();

        StringBuilder params = new StringBuilder();
        symbols.forEach(symbol -> params.append("\"").append(symbol).append("@depth@1000ms").append("\"").append(","));
        params.deleteCharAt(params.length() - 1);
        String payload = "{" +
                "\"method\": " + "\"SUBSCRIBE\"" + "," +
                "\"params\": " + "[" + params + "]" + "," +
                "\"id\": " + null +
                "}";

        connectWebSocket(payload, coinMap);
    }

    private void submitExecutorService() {
        executorService.submit(() -> {
            while (true) {
                try {
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void connectWebSocket(String payload, Map<String, Coin> coinMap) {
        HttpClient client = HttpClient.create()
                .keepAlive(true)
                .option(ChannelOption.SO_KEEPALIVE, true);

        client.websocket(WebsocketClientSpec.builder()
                    .maxFramePayloadLength(1048576)
                    .build())
            .uri(WSS_URL)
            .handle((inbound, outbound) -> {
                inbound.receive()
                    .asString()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> {
                        log.error("Потеряно соединение с Websocket");
                        executorService.shutdownNow();
                    })
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(2))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(depthList -> {
                        if (depthList != null && !depthList.isEmpty()) {
                            taskQueue.offer(() -> saveOrderBooks(createOrderBooks(coinMap, depthList)));
                        }
                    });

                return outbound.sendString(Mono.just(payload)).neverComplete();
            })
            .subscribe();
    }

    private Optional<BinanceCoinDepth> processWebsocketResponse(String response) {
        try {
            return Optional.of(objectMapper.readValue(response, BinanceCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.info(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<BinanceCoinDepth> coinDepth) {
        return coinDepth.isPresent() &&
                coinDepth.get().getB() != null &&
                coinDepth.get().getA() != null;
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<BinanceCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                    Coin currentCoin = coinMap.get(depth.getS().replaceAll("USDT", ""));
                    return getCurrentOrderBook(depth, currentCoin);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(BinanceCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = BinanceCoinDepthBuilder.getCoinDepth(currentCoin, depth, NAME);
        OrdersBook ordersBook = OrdersBookUtils.createOrderBooks(coinDepth);

        if (ordersBook.getBids().isEmpty() || ordersBook.getAsks().isEmpty()) return Optional.empty();

        return ordersBookRepository.findBySlug(ordersBook.getSlug())
                .map(book -> OrdersBookUtils.updateOrderBooks(book, coinDepth))
                .or(() -> Optional.of(ordersBook));
    }

    private void saveOrderBooks(Set<OrdersBook> ordersBookSet) {
        ordersBookRepository.saveAllAndFlush(ordersBookSet);
    }
}
