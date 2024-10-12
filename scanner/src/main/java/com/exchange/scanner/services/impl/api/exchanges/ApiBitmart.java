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
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Bitmart.BitmartCoinDepthBuilder;
import com.exchange.scanner.services.utils.Bitmart.BitmartSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class ApiBitmart implements ApiExchange {

    @Value("${exchanges.apiKeys.Bitmart.key}")
    private String key;

    @Value("${exchanges.apiKeys.Bitmart.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.Bitmart.memo}")
    private String memo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://ws-manager-compress.bitmart.com/api?protocol=1.1";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(10);

    private static final String NAME = "Bitmart";

    public final static String BASE_ENDPOINT = "https://api-cloud.bitmart.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 400;

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
            .filter(symbol -> symbol.getQuoteCurrency().equals("USDT") && !symbol.getBaseCurrency().startsWith("$") &&
                    symbol.getTradeStatus().equals("trading"))
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
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

    private Mono<BitmartVolumeTicker> getCoinTickerVolume() {
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
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
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
        List<List<String>> batches = ListUtils.partition(symbols, 20);
        Flux.fromIterable(batches).flatMap(batch -> outbound.sendString(Mono.just(createArgs(batch))))
            .delaySubscription(Duration.ofMillis(10))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private String createArgs(List<String> batch) {
        StringBuilder args = new StringBuilder();
        batch.forEach(symbol -> {
            args.append("\"").append("spot/depth20:").append(symbol).append("\"").append(",");
        });
        args.deleteCharAt(args.length() - 1);
        return String.format(
            "{" +
                "\"op\": \"subscribe\"," +
                "\"args\": [%s]" +
            "}", args);
    }

    private static Flux<Void> getPingFlux(WebsocketOutbound outbound) {
        return Flux.interval(Duration.ofSeconds(15))
            .flatMap(tick -> {
                String pingMessage = "ping";
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

    private Optional<BitmartCoinDepth> processWebsocketResponse(String response) {
        if (response.contains("pong")) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(response, BitmartCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<BitmartCoinDepth> depth) {
        return depth.isPresent() &&
            depth.get().getData() != null && !depth.get().getData().isEmpty() &&
            depth.get().getData().getFirst().getAsks() != null &&
            !depth.get().getData().getFirst().getAsks().isEmpty() &&
            depth.get().getData().getFirst().getBids() != null &&
            !depth.get().getData().getFirst().getBids().isEmpty() &&
            depth.get().getData().getFirst().getSymbol() != null;
    }

    private void processResult(
            Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, List<BitmartCoinDepth> depthList, ReentrantLock lock
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

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<BitmartCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getData().getFirst().getSymbol().replaceAll("_USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(BitmartCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = BitmartCoinDepthBuilder.getCoinDepth(currentCoin, depth.getData().getFirst(), NAME);
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
