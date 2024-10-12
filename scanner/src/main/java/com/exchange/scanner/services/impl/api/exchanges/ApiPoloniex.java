package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.poloniex.chains.PoloniexChain;
import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.poloniex.coins.PoloniexCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.poloniex.tickervolume.PoloniexVolumeData;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.poloniex.tradingfee.PoloniexTradingFeeResponse;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Poloniex.PoloniexCoinDepthBuilder;

import com.exchange.scanner.services.utils.Poloniex.PoloniexSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiPoloniex implements ApiExchange {

    @Value("${exchanges.apiKeys.Poloniex.key}")
    private String key;

    @Value("${exchanges.apiKeys.Poloniex.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://ws.poloniex.com/ws/public";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private static final String NAME = "Poloniex";

    public final static String BASE_ENDPOINT = "https://api.poloniex.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiPoloniex() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        List<PoloniexCurrencyResponse> response = getCurrencies().collectList().block();

        if (response == null || response.getFirst() == null) return coins;

        coins = response.stream()
                .filter(symbol -> symbol.getQuoteCurrencyName().equals("USDT") && symbol.getState().equals("NORMAL"))
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCurrencyName().toUpperCase() + "_USDT" + "/?type=spot");
                    return ObjectUtils.getCoin(symbol.getBaseCurrencyName(), NAME, links, symbol.getCrossMargin().getSupportCrossMargin());
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Flux<PoloniexCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/markets")
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
            .bodyToFlux(PoloniexCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        Map<String, PoloniexChain> response = getChains().collectList().block().stream()
                .filter(responseList -> !responseList.isEmpty())
                .flatMap(responseList -> responseList.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<String> coinNames = coins.stream().map(Coin::getName).toList();
        Map<String, PoloniexChain> filteredResponse = response.entrySet().stream()
                .filter(entry -> coinNames.contains(entry.getKey().toUpperCase()))
                .filter(entry -> entry.getValue().getWalletDepositState().equalsIgnoreCase("ENABLED") &&
                        entry.getValue().getWalletWithdrawalState().equalsIgnoreCase("ENABLED")
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            filteredResponse.forEach((key, value) -> {
                if (key.equalsIgnoreCase(coin.getName())) {
                    String chainName = CoinChainUtils.unifyChainName(value.getBlockchain());
                    Chain chain = new Chain();
                    chain.setName(chainName.toUpperCase());
                    chain.setCommission(new BigDecimal(value.getWithdrawalFee()));
                    chain.setMinConfirm(value.getMinConf());
                    chains.add(chain);
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Flux<Map<String, PoloniexChain>> getChains() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/currencies")
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
            .bodyToFlux(new ParameterizedTypeReference<Map<String, PoloniexChain>>() {})
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public  Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        PoloniexTradingFeeResponse response = getFee().block();
        String takerFee;
        if (response == null) {
            takerFee = BigDecimal.ZERO.toPlainString();
        } else {
            takerFee = response.getTakerRate();
        }

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    takerFee
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    private Mono<PoloniexTradingFeeResponse> getFee() {
        String requestPath = "/feeinfo";
        TreeMap<String, String> params = new TreeMap<>();
        PoloniexSignatureBuilder signatureBuilder = new PoloniexSignatureBuilder(secret, "GET", requestPath, params);
        signatureBuilder.createSignature();

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                return uriBuilder.build();
            })
            .header("key", key)
            .header("signatureMethod", "hmacSHA256")
            .header("signatureVersion", "1")
            .header("signTimestamp", String.valueOf(signatureBuilder.getTimestamp()))
            .header("signature", signatureBuilder.getSignature())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(PoloniexTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        List<PoloniexVolumeData> response = getCoinTicker().collectList().block();
        if (response == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "_USDT").toList();
        List<PoloniexVolumeData> volumeData = response.stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            volumeData.forEach(data -> {
                if (data.getSymbol().equalsIgnoreCase(coin.getName() + "_USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getAmount()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Flux<PoloniexVolumeData> getCoinTicker() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/markets/ticker24h")
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
            .bodyToFlux(PoloniexVolumeData.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange, BlockingDeque<Runnable> taskQueue, ReentrantLock lock) {
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "_USDT").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(coin -> coin.getName().toUpperCase(), coin -> coin));
        HttpClient client = createClient();

        connect(symbols, coinMap, taskQueue, client, lock);
    }

    private HttpClient createClient() {
        return HttpClient.create()
            .keepAlive(true)
            .option(ChannelOption.SO_KEEPALIVE, true);
    }

    private void connect(
            List<String> symbols, Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, HttpClient client, ReentrantLock lock
    ) {
        Hooks.onErrorDropped(error -> log.error(error.getLocalizedMessage()));

        client.websocket()
            .uri(WSS_URL)
            .handle((inbound, outbound) -> {
                sendSubscribeMessage(symbols, outbound);
                Flux<Void> pingFlux = getPingFlux(outbound);
                inbound.receive()
                    .asString()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> processTerminate(symbols, coinMap, taskQueue, client, lock))
                    .onErrorResume(this::processError)
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(depthList -> processResult(coinMap, taskQueue, depthList, lock))
                    .subscribe();

                return outbound.then().thenMany(pingFlux);
            })
            .subscribe();
    }

    private void sendSubscribeMessage(List<String> symbols, WebsocketOutbound outbound) {
        List<List<String>> batches = ListUtils.partition(symbols, 500);
        Flux.fromIterable(batches).flatMap(batch -> outbound.sendString(Mono.just(createArgs(batch))))
            .delaySubscription(Duration.ofMillis(20))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private String createArgs(List<String> symbols) {
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> {
            args.append("\"").append(symbol).append("\"").append(",");
        });
        args.deleteCharAt(args.length() - 1);
        return String.format(
            "{ " +
                "\"event\": \"subscribe\", " +
                "\"channel\": [\"book\"], " +
                "\"symbols\": [%s], " +
                "\"depth\": 10" +
            "}", args);
    }

    private static Flux<Void> getPingFlux(WebsocketOutbound outbound) {
        return Flux.interval(Duration.ofSeconds(25))
                .flatMap(tick -> {
                    String pingMessage = "{\"event\": \"ping\" }";
                    return outbound.sendString(Mono.just(pingMessage)).then(Mono.empty());
                }).onErrorResume(error -> {
                    log.debug(error.getLocalizedMessage());
                    return Mono.empty();
                });
    }

    private void processTerminate(
            List<String> symbols, Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, HttpClient client, ReentrantLock lock
    ) {
        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
        reconnect(symbols, coinMap, taskQueue, client, lock);
    }

    private void reconnect(
            List<String> symbols, Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, HttpClient client, ReentrantLock lock
    ) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
            .subscribe(aLong -> connect(symbols, coinMap, taskQueue, client, lock));
    }

    private Mono<String> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private Optional<PoloniexCoinDepth> processWebsocketResponse(String response) {
        try {
            return Optional.of(objectMapper.readValue(response, PoloniexCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<PoloniexCoinDepth> depth) {
        return depth.isPresent() &&
            depth.get().getData() != null &&
            depth.get().getData().getFirst().getAsks() != null &&
            !depth.get().getData().getFirst().getAsks().isEmpty() &&
            depth.get().getData().getFirst().getBids() != null &&
            !depth.get().getData().getFirst().getBids().isEmpty();
    }

    private void processResult(
            Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, List<PoloniexCoinDepth> depthList, ReentrantLock lock
    ) {
        if (depthList != null && !depthList.isEmpty()) {
            try {
                lock.lock();
                saveOrderBooks(createOrderBooks(coinMap, depthList));
            } finally {
                lock.unlock();
            }
        }
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<PoloniexCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getData().getFirst().getSymbol().replaceAll("_USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(PoloniexCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = PoloniexCoinDepthBuilder.getPoloniexCoinDepth(depth, currentCoin, NAME);
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
