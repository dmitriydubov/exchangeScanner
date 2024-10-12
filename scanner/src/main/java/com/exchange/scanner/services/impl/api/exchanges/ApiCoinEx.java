package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.coinex.chains.CoinexChainsData;
import com.exchange.scanner.dto.response.exchangedata.coinex.chains.CoinexChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinex.tickervolume.CoinExVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.coinex.coins.CoinExCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.coinex.tickervolume.CoinExVolumeTickerData;
import com.exchange.scanner.dto.response.exchangedata.coinex.tradingfee.CoinexFeeData;
import com.exchange.scanner.dto.response.exchangedata.coinex.tradingfee.CoinexTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Coinex.CoinExCoinDepthBuilder;
import com.exchange.scanner.services.utils.Coinex.CoinexSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class ApiCoinEx implements ApiExchange {

    @Value("${exchanges.apiKeys.Coinex.key}")
    private String key;

    @Value("${exchanges.apiKeys.Coinex.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://socket.coinex.com/v2/spot";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private static final String NAME = "CoinEx";

    public final static String BASE_ENDPOINT = "https://api.coinex.com/v2";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiCoinEx() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        CoinExCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
            .filter(symbol -> symbol.getQuoteCcy().equals("USDT"))
            .map(symbol -> {
                LinkDTO links = new LinkDTO();
                links.setDepositLink(exchange.getDepositLink());
                links.setWithdrawLink(exchange.getWithdrawLink());
                links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCcy().toLowerCase() + "-usdt" + "#spot");
                return ObjectUtils.getCoin(symbol.getBaseCcy(), NAME, links, symbol.getIsMarginAvailable());
            })
            .collect(Collectors.toSet());

        return coins;
    }

    private Mono<CoinExCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/spot/market")
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
            .bodyToMono(CoinExCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        CoinexChainsResponse response = getChains().block();

        if (response == null || response.getData() == null) return chainsDTOSet;
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());
        List<CoinexChainsData> filteredData = response.getData().stream()
            .filter(data -> coinsNames.contains(data.getAsset().getCcy()))
            .filter(data -> data.getChains().stream()
                    .allMatch(chain -> chain.getDepositEnabled() && chain.getWithdrawEnabled())
            )
            .toList();

        coins.forEach(coin -> filteredData.forEach(data -> {
            if (coin.getName().equals(data.getAsset().getCcy())) {
                Set<Chain> chains = new HashSet<>();
                data.getChains().forEach(chainResponse -> {
                    String chainName = CoinChainUtils.unifyChainName(chainResponse.getChain().toUpperCase());
                    Chain chain = new Chain();
                    chain.setName(chainName);
                    chain.setCommission(new BigDecimal(chainResponse.getWithdrawalFee()));
                    chain.setMinConfirm(chainResponse.getIrreversibleConfirmations());
                    chains.add(chain);
                });
                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            }
        }));

        return chainsDTOSet;
    }

    private Mono<CoinexChainsResponse> getChains() {
        String requestPath = "/assets/all-deposit-withdraw-config";
        Map<String, String> params = new HashMap<>();
        CoinexSignatureBuilder signatureBuilder = new CoinexSignatureBuilder(secret, params);
        signatureBuilder.createSignature("GET", requestPath);

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                return uriBuilder.build();
            })
            .header("X-COINEX-KEY", key)
            .header("X-COINEX-SIGN", signatureBuilder.getSignature())
            .header("X-COINEX-TIMESTAMP", signatureBuilder.getTimestamp())
            .retrieve()
            .onStatus(
                status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                    log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                    return Mono.empty();
                })
            )
            .bodyToMono(CoinexChainsResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        CoinexTradingFeeResponse response = getFee().block();
        if (response == null || response.getData() == null) return tradingFeeSet;
        List<String> symbols = coins.stream().map(Coin::getName).toList();
        List<CoinexFeeData> data = response.getData().stream()
            .filter(fee -> fee.getQuoteCcy().equalsIgnoreCase("USDT") && symbols.contains(fee.getBaseCcy()))
            .toList();

        coins.forEach(coin -> {
            data.forEach(fee -> {
                if (fee.getBaseCcy().equalsIgnoreCase(coin.getName())) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            fee.getTakerFeeRate()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Mono<CoinexTradingFeeResponse> getFee() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/spot/market")
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
            .bodyToMono(CoinexTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        CoinExVolumeTicker response = getCoinTicker().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<CoinExVolumeTickerData> dataVolume = response.getData().stream()
            .filter(data -> symbols.contains(data.getMarket()))
            .toList();

        coins.forEach(coin -> dataVolume.forEach(data -> {
            if (data.getMarket().equalsIgnoreCase(coin.getName() + "USDT")) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                    exchange,
                    coin,
                    data.getValue()
                );

                volume24HSet.add(responseDTO);
            }
        }));

        return volume24HSet;
    }

    private Mono<CoinExVolumeTicker> getCoinTicker() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/spot/ticker")
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
            .bodyToMono(CoinExVolumeTicker.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange, BlockingDeque<Runnable> taskQueue, ReentrantLock lock) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
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
                List<List<String>> batches = ListUtils.partition(symbols, 100);
                List<String> args = batches.stream().map(this::createArgs).toList();
                sendSubscribeMessage(args, outbound);
                Flux<Void> sendPingMessage = sendPingMessage(outbound, new JSONObject(args.getFirst()).getInt("id"));
                inbound.receiveFrames()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> processTerminate(symbols, coinMap, taskQueue, client, lock))
                    .onErrorResume(this::processError)
                    .flatMap(this::decompressGZIPData)
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(depthList -> processResult(coinMap, taskQueue, depthList, lock))
                    .subscribe();

                return outbound.then().thenMany(sendPingMessage);
            })
            .subscribe();
    }

    private void sendSubscribeMessage(List<String> args, WebsocketOutbound outbound) {
        Flux.fromIterable(args).flatMap(payload -> outbound.sendString(Mono.just(payload)))
            .delaySubscription(Duration.ofMillis(10))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private String createArgs(List<String> symbols) {
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> {
            args.append("[").append("\"").append(symbol).append("\"").append(", ").append(10).append(", ").append("\"").append("0").append("\"").append(", ").append(true).append("]");
            args.append(",");
        });
        args.deleteCharAt(args.length() - 1);

        int userId = new Random().nextInt(0, 1_000_000);
        return String.format(
            "{" +
                "\"method\": " + "\"depth.subscribe\", " +
                "\"params\": " + "{" +
                "\"market_list\": [" + "%s" + "]" +
                "}," +
                "\"id\": " + userId +
            "}", args);
    }

    private Flux<Void> sendPingMessage(WebsocketOutbound outbound, int id) {
        return Flux.interval(Duration.ofSeconds(20))
            .flatMap(tick -> outbound.send(
                Mono.just(String.format("{\"method\": \"server.ping\", \"params\": {}, \"id\": %s}", id)).then(Mono.empty()))
            );
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

    private Mono<WebSocketFrame> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private Mono<String> decompressGZIPData(WebSocketFrame frame) {
        ByteBuf byteBuf = frame.content();
        byte[] compressedData = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(compressedData);

        try {
            String decompressData = getDecompressedData(compressedData);
            return Mono.just(decompressData);
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private String getDecompressedData(byte[] compressedData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(compressedData);
        GZIPInputStream gzipInputStream = new GZIPInputStream(in);

        byte[] buffer = new byte[1024];
        int len;

        while ((len = gzipInputStream.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        gzipInputStream.close();
        out.close();
        return out.toString(StandardCharsets.UTF_8);
    }

    private Optional<CoinExCoinDepth> processWebsocketResponse(String response) {
        try {
            return Optional.of(objectMapper.readValue(response, CoinExCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<CoinExCoinDepth> depth) {
        return depth.isPresent() && depth.get().getData() != null &&
            depth.get().getData().getDepth() != null &&
            depth.get().getData().getDepth().getAsks() != null &&
            !depth.get().getData().getDepth().getAsks().isEmpty() &&
            depth.get().getData().getDepth().getBids() != null &&
            !depth.get().getData().getDepth().getBids().isEmpty() &&
            depth.get().getData().getMarket() != null;
    }

    private void processResult(
            Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, List<CoinExCoinDepth> depthList, ReentrantLock lock
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

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<CoinExCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getData().getMarket().replaceAll("USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(CoinExCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = CoinExCoinDepthBuilder.getCoinDepth(currentCoin, depth.getData().getDepth(), NAME);
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
