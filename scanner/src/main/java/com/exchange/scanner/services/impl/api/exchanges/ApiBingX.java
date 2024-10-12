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
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.BingX.BingXCoinDepthBuilder;
import com.exchange.scanner.services.utils.BingX.BingXSignatureBuilder;
import com.exchange.scanner.services.utils.Huobi.HuobiCoinDepthBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import jakarta.annotation.security.RunAs;
import lombok.RequiredArgsConstructor;
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
import reactor.util.retry.Retry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiBingX implements ApiExchange {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://open-api-ws.bingx.com/market";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    @Value("${exchanges.apiKeys.BingX.key}")
    private String key;

    @Value("${exchanges.apiKeys.BingX.secret}")
    private String secret;

    private static final String NAME = "BingX";

    public final static String BASE_ENDPOINT = "https://open-api.bingx.com";

    private static final int TIMEOUT = 10000;

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
    public void getOrderBook(Set<Coin> coins, String exchange, BlockingDeque<Runnable> taskQueue, ReentrantLock lock) {
        List<String> symbols = coins.stream().limit(60).map(coin -> coin.getName().toUpperCase() + "-USDT").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(coin -> coin.getName().toUpperCase(), coin -> coin));
        HttpClient client = createClient();
        int batchSize = 1;
        List<List<String>> batches = ListUtils.partition(symbols, batchSize);
        batches.forEach(batch -> createArgsAndConnectWebsocket(batch, coinMap, taskQueue, client));
    }

    private HttpClient createClient() {
        return HttpClient.create()
                .keepAlive(true)
                .option(ChannelOption.SO_KEEPALIVE, true);
    }

    private void createArgsAndConnectWebsocket(
            List<String> symbols, Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, HttpClient client
    ) {
        String userId = UUID.randomUUID().toString();
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> args.append(symbol).append("@").append("depth10").append("/"));
        args.deleteCharAt(args.length() - 1);

        String payload = String.format(
            "{" +
                "\"id\":\"%s\"," +
                "\"reqType\":\"sub\"," +
                "\"dataType\":\"%s\"" +
            " }", userId, args);

        connect(payload, coinMap, taskQueue, client);
    }

    private void connect(String payload, Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, HttpClient client) {
        Hooks.onErrorDropped(error -> log.error(error.getLocalizedMessage()));

        client.websocket()
            .uri(WSS_URL)
            .handle((inbound, outbound) -> {
                inbound.receiveFrames()
                    .doOnTerminate(() -> {
                        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
                        reconnect(payload, coinMap, taskQueue, client);
                    })
                    .onErrorResume(error -> {
                        log.error(error.getLocalizedMessage());
                        return Mono.empty();
                    })
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .flatMap(this::decompressGZIPData)
                    .doOnNext(response -> {
                        if (response.contains("ping")) {
                            String pongResponse = extractPing(response);
                            outbound.sendString(Mono.just(pongResponse)).then().subscribe();
                        }
                    })
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(depthList -> {
                        if (depthList != null && !depthList.isEmpty()) {
                            taskQueue.offer(() -> saveOrderBooks(createOrderBooks(coinMap, depthList)));
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException();
                            }
                        }
                    })
                    .subscribe();

                return outbound.sendString(Mono.just(payload)).neverComplete();
            })
            .subscribe();
    }

    private void reconnect(String payload, Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, HttpClient client) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
            .subscribe(aLong -> connect(payload, coinMap, taskQueue, client));
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

    private String extractPing(String response) {
        return response.replaceAll("ping", "pong");
    }

    private Optional<HuobiCoinDepth> processWebsocketResponse(String response) {
        try {
            System.out.println(response);
            return Optional.of(objectMapper.readValue(response, HuobiCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.error(e.getLocalizedMessage());
            return Optional.empty();
        }
    }

    private boolean isValidResponseData(Optional<HuobiCoinDepth> depth) {
        return depth.isPresent() &&
                depth.get().getCh() != null && depth.get().getTick() != null &&
                !depth.get().getTick().getAsks().isEmpty() && !depth.get().getTick().getBids().isEmpty();
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<HuobiCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                    Coin currentCoin = coinMap.get(getCoinMapKey(depth.getCh()));
                    return getCurrentOrderBook(depth, currentCoin);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private String getCoinMapKey(String ch) {
        String[] depthChannelParts = ch.split("\\.");
        if (depthChannelParts.length < 2) return "";
        return depthChannelParts[1].replaceAll("usdt", "");
    }

    private Optional<OrdersBook> getCurrentOrderBook(HuobiCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = HuobiCoinDepthBuilder.getCoinDepth(currentCoin, depth.getTick(), NAME);
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
