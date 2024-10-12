package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.gateio.chains.ChainDTO;
import com.exchange.scanner.dto.response.exchangedata.gateio.coins.GateIoCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.gateio.depth.GateIOCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.gateio.volume24h.GateIOCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.gateio.tradingfee.GateIOTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.GateIO.GateIOCoinDepthBuilder;
import com.exchange.scanner.services.utils.GateIO.GateIOSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
public class ApiGateIO implements ApiExchange {

    @Value("${exchanges.apiKeys.GateIO.key}")
    private String key;

    @Value("${exchanges.apiKeys.GateIO.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String NAME = "Gate.io";

    private final static String BASE_HTTP_ENDPOINT = "https://api.gateio.ws/api/v4";

    private static final int HTTP_REQUEST_TIMEOUT = 10000;

    private static final String WSS_URL = "wss://api.gateio.ws/ws/v4/";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private final WebClient webClient;

    public ApiGateIO() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_HTTP_ENDPOINT, HTTP_REQUEST_TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();
        List<GateIoCurrencyResponse> response = getCurrencies().collectList().block();
        if (response == null || response.getFirst() == null) return coins;

        coins = response.stream()
            .filter(currency -> currency.getQuote().equals("USDT") && !currency.getBase().endsWith("3S") &&
                 !currency.getBase().endsWith("3L") && currency.getTradeStatus().equals("tradable"))
            .map(currency -> {
                LinkDTO links = new LinkDTO();
                links.setDepositLink(exchange.getDepositLink() + currency.getBase().toUpperCase());
                links.setWithdrawLink(exchange.getWithdrawLink() + currency.getBase().toUpperCase());
                links.setTradeLink(exchange.getTradeLink() + currency.getBase().toUpperCase() + "_USDT");
                return ObjectUtils.getCoin(currency.getBase(), NAME, links, false);
            })
            .collect(Collectors.toSet());

        return coins;
    }

    private Flux<GateIoCurrencyResponse> getCurrencies() {

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/currency_pairs")
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
            .bodyToFlux(GateIoCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        List<ChainDTO> response = getChains().collectList().block();
        if (response == null || response.isEmpty()) return chainsDTOSet;

        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<ChainDTO> filteredChainDTO = response.stream()
                .filter(data -> coinsNames.contains(data.getCurrency()) &&
                        !data.getDepositDisabled() &&
                        !data.getWithdrawDisabled()
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            filteredChainDTO.forEach(chainDTO -> {
                if (chainDTO.getCurrency().equals(coin.getName())) {
                    String chainName = CoinChainUtils.unifyChainName(chainDTO.getChain());
                    Chain chain = new Chain();
                    chain.setName(chainName);
                    chain.setCommission(BigDecimal.ZERO);
                    chain.setMinConfirm(0);
                    chains.add(chain);
                }
            });

            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Flux<ChainDTO> getChains() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/currencies")
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
            .bodyToFlux(ChainDTO.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        GateIOTradingFeeResponse response = getFee().block();
        if (response == null || response.getTakerFee() == null) return tradingFeeSet;

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    response.getTakerFee()
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    private Mono<GateIOTradingFeeResponse> getFee() {
        String endpoint = "/api/v4/wallet/fee";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        String method = "GET";
        String requestBody = "";
        String queryString = "";
        String payloadHash = GateIOSignatureBuilder.hashSHA512(requestBody);
        String data = method.toUpperCase() + "\n" +
                endpoint.trim() + "\n" +
                queryString.trim() + "\n" +
                payloadHash.trim() + "\n" +
                timestamp.trim();

        String signature = GateIOSignatureBuilder.generateGateIOSignature(secret.trim(), data.trim());

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/wallet/fee")
                    .build()
            )
            .header("KEY", key)
            .header("Timestamp", timestamp)
            .header("SIGN", signature)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(GateIOTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        List<GateIOCoinTickerVolume> response = getCoinTickerVolume().collectList().block();
        if (response == null || response.getFirst() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "_USDT").toList();
        List<GateIOCoinTickerVolume> filteredResponse = response.stream()
                .filter(data -> symbols.contains(data.getCurrencyPair()))
                .toList();

        coins.forEach(coin -> {
            filteredResponse.forEach(data -> {
                if (data.getCurrencyPair().equalsIgnoreCase(coin.getName().toUpperCase() + "_USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getQuoteVolume()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Flux<GateIOCoinTickerVolume> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/tickers")
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
            .bodyToFlux(GateIOCoinTickerVolume.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange, BlockingDeque<Runnable> taskQueue, ReentrantLock lock) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "_USDT").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(Coin::getName, coin -> coin));
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
                inbound.receive()
                    .asString()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> processTerminate(symbols, coinMap, taskQueue, client, lock))
                    .onErrorResume(ApiGateIO::processError)
                    .doOnNext(this::processReceivePingMessage)
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(depthList -> processResult(coinMap, taskQueue, depthList, lock))
                    .subscribe();

                return outbound.neverComplete();
            })
            .subscribe();
    }

    private void sendSubscribeMessage(List<String> symbols, WebsocketOutbound outbound) {
        List<List<String>> batches = ListUtils.partition(symbols, 100);
        Flux.fromIterable(batches).flatMap(batch -> outbound.sendString(Flux.fromIterable(createArgs(batch))))
            .delaySubscription(Duration.ofMillis(200))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private List<String> createArgs(List<String> symbols) {
        return symbols.stream()
            .map(symbol -> String.format(
                "{" +
                    "\"channel\": \"spot.order_book\", " +
                    "\"event\": \"subscribe\", " +
                    "\"payload\": [\"%s\", \"10\", \"1000ms\"]" +
                "}", symbol)
            )
            .toList();
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

    private static Mono<String> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private void processReceivePingMessage(String response) {
        if (response.contains("ping")) {
            log.debug(response);
        }
    }

    private Optional<GateIOCoinDepth> processWebsocketResponse(String response) {
        try {
            GateIOCoinDepth gateIOCoinDepth = objectMapper.readValue(response, GateIOCoinDepth.class);
            if (gateIOCoinDepth.getEvent().equalsIgnoreCase("subscribe")) return Optional.empty();
            return Optional.of(gateIOCoinDepth);
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<GateIOCoinDepth> coinDepth) {
        return coinDepth.isPresent() &&
            !coinDepth.get().getResult().getAsks().isEmpty() &&
            !coinDepth.get().getResult().getBids().isEmpty();
    }

    private void processResult(
            Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, List<GateIOCoinDepth> depthList, ReentrantLock lock
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

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<GateIOCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getResult().getS().replaceAll("_USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(GateIOCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = GateIOCoinDepthBuilder.getCoinDepth(currentCoin, depth, NAME);
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
