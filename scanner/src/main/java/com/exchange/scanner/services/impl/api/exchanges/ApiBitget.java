package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bitget.chains.BitgetChainData;
import com.exchange.scanner.dto.response.exchangedata.bitget.chains.BitgetChainResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.depth.BitgetCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitget.coins.BitgetCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.tickervolume.BitgetTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bitget.tickervolume.BitgetTickerVolumeData;
import com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee.BitgetTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee.Data;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Bitget.BitgetDepthBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ApiBitget implements ApiExchange {

    @Autowired
    private OrdersBookRepository ordersBookRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String NAME = "Bitget";

    public final static String BASE_ENDPOINT = "https://api.bitget.com";

    private static final String WSS_URL = "wss://ws.bitget.com/v2/ws/public";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 500;

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private final WebClient webClient;

    public ApiBitget() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Set<Coin> coins = new HashSet<>();

        BitgetCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQuoteCoin().equals("USDT") && symbol.getStatus().equals("online"))
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCoin().toUpperCase() + "USDT");
                    return ObjectUtils.getCoin(symbol.getBaseCoin(), NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BitgetCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/public/symbols")
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
            .bodyToMono(BitgetCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        BitgetChainResponse response = getChain().block();
        if (response == null || response.getData().isEmpty()) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<BitgetChainData> chainData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCoin()))
                .filter(data -> data.getChains().stream()
                    .allMatch(chain -> chain.getWithdrawable().equalsIgnoreCase("true") &&
                            chain.getRechargeable().equalsIgnoreCase("true")
                    )
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainData.forEach(data -> {
                if (data.getCoin().equals(coin.getName())) {
                    data.getChains().forEach(chainsDTO -> {
                        String chainName = CoinChainUtils.unifyChainName(chainsDTO.getChain());
                        Chain chain = new Chain();
                        chain.setName(chainName.toUpperCase());
                        chain.setCommission(new BigDecimal(chainsDTO.getWithdrawFee()));
                        chain.setMinConfirm(Integer.valueOf(chainsDTO.getWithdrawConfirm()));
                        chains.add(chain);
                    });
                }
            });

            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<BitgetChainResponse> getChain() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/public/coins")
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
            .bodyToMono(BitgetChainResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        BitgetTradingFeeResponse response = getFee().block();
        if (response == null || response.getData().isEmpty()) return tradingFeeSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
        List<Data> bitgetFeeData = response.getData().stream()
                .filter(feeData -> symbols.contains(feeData.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            bitgetFeeData.forEach(feeData -> {
                if (feeData.getSymbol().equals(coin.getName() + "USDT")) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            feeData.getTakerFeeRate()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Mono<BitgetTradingFeeResponse> getFee() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/public/symbols")
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitgetTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        BitgetTickerVolume response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
        List<BitgetTickerVolumeData> volumeData = response.getData().stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            volumeData.forEach(data -> {
                if (data.getSymbol().equals(coin.getName() + "USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getUsdtVolume()
                    );

                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<BitgetTickerVolume> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/market/tickers")
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
            .bodyToMono(BitgetTickerVolume.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange, BlockingDeque<Runnable> taskQueue, ReentrantLock lock) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
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
        List<List<String>> batches = ListUtils.partition(symbols, 50);
        Flux.fromIterable(batches).flatMap(batch -> outbound.sendString(Mono.just(createArgs(batch))))
                .delaySubscription(Duration.ofMillis(20))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private static Flux<Void> getPingFlux(WebsocketOutbound outbound) {
        return Flux.interval(Duration.ofSeconds(20))
            .flatMap(tick -> {
                String pingMessage = "ping";
                return outbound.sendString(Mono.just(pingMessage)).then(Mono.empty());
            }).onErrorResume(error -> {
                log.debug(error.getLocalizedMessage());
                return Mono.empty();
            });
    }

    private String createArgs(List<String> symbols) {
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> {
            args.append("{");
            args.append("\"").append("instType").append("\"").append(": ").append("\"").append("SPOT").append("\"").append(",");
            args.append(" \"").append("channel").append("\"").append(": ").append("\"").append("books15").append("\"").append(",");
            args.append(" \"").append("instId").append("\"").append(": ").append(" \"").append(symbol).append("\"");
            args.append("}").append(",");
        });
        args.deleteCharAt(args.length() - 1);
        return
            "{" +
                "\"op\": " + "\"subscribe\"," +
                " \"args\": " + "[" + args + "]" +
            "}";
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

    private Optional<BitgetCoinDepth> processWebsocketResponse(String response) {
        try {
            if (response.contains("pong")) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(response, BitgetCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<BitgetCoinDepth> depth) {
        return depth.isPresent() &&
            depth.get().getData() != null &&
            depth.get().getArg() != null && depth.get().getArg().getInstId() != null &&
            depth.get().getData().getFirst().getAsks() != null &&
            !depth.get().getData().getFirst().getAsks().isEmpty() &&
            depth.get().getData().getFirst().getBids() != null &&
            !depth.get().getData().getFirst().getBids().isEmpty();
    }

    private void processResult(
            Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, List<BitgetCoinDepth> depthList, ReentrantLock lock
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

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<BitgetCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getArg().getInstId().replaceAll("USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(BitgetCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = BitgetDepthBuilder.getCoinDepth(currentCoin, depth.getData().getFirst(), NAME);
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
