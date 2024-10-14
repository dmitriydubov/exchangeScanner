package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.huobi.chains.HuobiChainsData;
import com.exchange.scanner.dto.response.exchangedata.huobi.chains.HuobiChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.huobi.coins.HuobiCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume.HuobiVolumeData;
import com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume.HuobiVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiFeeData;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Huobi.HuobiCoinDepthBuilder;
import com.exchange.scanner.services.utils.Huobi.HuobiSignatureBuilder;
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
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class ApiHuobi implements ApiExchange {

    @Value("${exchanges.apiKeys.Huobi.key}")
    private String key;

    @Value("${exchanges.apiKeys.Huobi.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String NAME = "Huobi";

    public final static String BASE_ENDPOINT = "https://api.huobi.pro";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final String WSS_URL = "wss://api.huobi.pro/ws";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private final WebClient webClient;

    public ApiHuobi() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        HuobiCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQcdn().equals("USDT") && symbol.getTe())
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + symbol.getBcdn().toLowerCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + symbol.getBcdn().toLowerCase());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBcdn().toLowerCase() + "_usdt" + "?type=spot");
                    return ObjectUtils.getCoin(symbol.getBcdn(), NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<HuobiCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/settings/common/symbols")
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
            .bodyToMono(HuobiCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        HuobiChainsResponse response = getChains().block();
        if (response == null || response.getData() == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<HuobiChainsData> chainsData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCurrency().toUpperCase()))
                .filter(data -> data.getChains().stream()
                        .allMatch(chain -> chain.getDepositStatus().equals("allowed") &&
                                chain.getWithdrawStatus().equals("allowed"))
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainsData.forEach(data -> {
                if (data.getCurrency().equalsIgnoreCase(coin.getName())) {
                    data.getChains().forEach(chainResponse -> {
                        String chainName = CoinChainUtils.unifyChainName(chainResponse.getDisplayName());
                        Chain chain = new Chain();
                        chain.setName(chainName.toUpperCase());
                        if (chainResponse.getTransactFeeWithdraw() == null) {
                            chain.setCommission(new BigDecimal(chainResponse.getTransactFeeRateWithdraw()));
                        } else {
                            chain.setCommission(new BigDecimal(chainResponse.getTransactFeeWithdraw()));
                        }
                        chain.setMinConfirm(chainResponse.getNumOfConfirmations());
                        chains.add(chain);
                    });
                }
            });

            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<HuobiChainsResponse> getChains() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/reference/currencies")
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
            .bodyToMono(HuobiChainsResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        List<HuobiTradingFeeResponse> response = getFee(coins).collectList().block();

        if (response == null) return tradingFeeSet;
        List<HuobiFeeData> feeData = response.stream()
                .flatMap(list -> list.getData().stream())
                .toList();

        coins.forEach(coin -> feeData.forEach(feeResponse -> {
            if ((coin.getName() + "usdt").equalsIgnoreCase(feeResponse.getSymbol())) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        feeResponse.getTakerFeeRate()
                );
                tradingFeeSet.add(responseDTO);
            }
        }));

        return tradingFeeSet;
    }

    private Flux<HuobiTradingFeeResponse> getFee(Set<Coin> coins) {
        int requestSymbolMaxSize = 10;
        List<List<Coin>> partitions = ListUtils.partition(new ArrayList<>(coins), requestSymbolMaxSize);
        String requestPath = "/v2/reference/transact-fee-rate";

        return Flux.fromIterable(partitions)
            .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
            .flatMap(partition -> {
                TreeMap<String, String> params = new TreeMap<>();
                params.put("symbols", generateSymbolsParameters(partition));
                HuobiSignatureBuilder signatureBuilder = new HuobiSignatureBuilder(
                        key, secret, requestPath, "GET", params
                );
                signatureBuilder.createSignature();

                return webClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(requestPath);
                        params.forEach(uriBuilder::queryParam);
                        uriBuilder.queryParam("Signature", signatureBuilder.getSignature());
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                                return Mono.empty();
                            })
                    )
                    .bodyToFlux(HuobiTradingFeeResponse.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .map(response -> {
                        if (response.getCode() == 200) {
                            return response;
                        } else {
                            HuobiTradingFeeResponse failedResponse = new HuobiTradingFeeResponse();
                            List<HuobiFeeData> feeDataList = new ArrayList<>();
                            partition.forEach(coin -> {
                                HuobiFeeData feeData = new HuobiFeeData();
                                feeData.setSymbol(coin.getName().toUpperCase() + "usdt");
                                feeData.setTakerFeeRate("0.002");
                                feeDataList.add(feeData);
                            });
                            failedResponse.setData(feeDataList);
                            return failedResponse;
                        }
                    })
                    .onErrorResume(error -> {
                        LogsUtils.createErrorResumeLogs(error, NAME);
                        return Flux.empty();
                    });
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        HuobiVolumeResponse response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "usdt").toList();
        List<HuobiVolumeData> volumeData = response.getData().stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            volumeData.forEach(data -> {
                if (data.getSymbol().equalsIgnoreCase(coin.getName().toLowerCase() + "usdt")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getVol()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<HuobiVolumeResponse> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/market/tickers")
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
            .bodyToMono(HuobiVolumeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "usdt").toList();
        Map<String, Coin> coinMap = coins.stream()
                .collect(Collectors.toMap(coin -> coin.getName().toLowerCase(), coin -> coin));
        HttpClient client = createClient();
        connect(symbols, coinMap, client);
    }

    private HttpClient createClient() {
        return HttpClient.create()
            .keepAlive(true)
            .option(ChannelOption.SO_KEEPALIVE, true);
    }

    private void connect(List<String> symbols, Map<String, Coin> coinMap, HttpClient client) {
        Hooks.onErrorDropped(error -> log.error(error.getLocalizedMessage()));

        client.websocket()
            .uri(WSS_URL)
            .handle((inbound, outbound) -> {
                sendSubscribeMessage(symbols, outbound);
                inbound.receiveFrames()
                    .doOnTerminate(() -> processTerminate(symbols, coinMap, client))
                    .onErrorResume(this::processError)
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .flatMap(this::decompressGZIPData)
                    .doOnNext(response -> processReceivePingMessage(outbound, response))
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(depthList -> processResult(coinMap, depthList))
                    .subscribe();

                return outbound.neverComplete();
            })
            .subscribe();
    }

    private void sendSubscribeMessage(List<String> symbols, WebsocketOutbound outbound) {
        Flux.fromIterable(symbols).flatMap(symbol -> outbound.sendString(Mono.just(createArgs(symbol))))
                .delaySubscription(Duration.ofMillis(20))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private String createArgs(String currency) {
        String userId = "id" + (new Random().nextInt(1_000_000) + 1);
        return String.format(
            "{ " +
                "\"sub\": \"market.%s.depth.step0\", " +
                "\"id\": \"%s\"" +
            " }", currency, userId);
    }

    private Mono<String> decompressGZIPData(WebSocketFrame frame) {
        ByteBuf byteBuf = frame.content();
        byte[] compressedData = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(compressedData);

        try {
            String decompressData = getDecompressedData(compressedData);
            return Mono.just(decompressData);
        } catch (IOException e) {
            log.debug(e.getLocalizedMessage());
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

    private void processTerminate(List<String> symbols, Map<String, Coin> coinMap, HttpClient client) {
        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
        reconnect(symbols, coinMap, client);
    }

    private void reconnect(List<String> symbols, Map<String, Coin> coinMap, HttpClient client) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
            .subscribe(aLong -> connect(symbols, coinMap, client));
    }

    private Mono<WebSocketFrame> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private void processReceivePingMessage(WebsocketOutbound outbound, String response) {
        if (response.contains("ping")) {
            String pongResponse = "{\"pong\":" + extractPing(response) + "}";
            outbound.sendString(Mono.just(pongResponse)).then().subscribe();
        }
    }

    private long extractPing(String response) {
        return new JSONObject(response).getLong("ping");
    }

    private Optional<HuobiCoinDepth> processWebsocketResponse(String response) {
        try {
            return Optional.of(objectMapper.readValue(response, HuobiCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
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
                if (currentCoin == null) return Optional.<OrdersBook>empty();
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

    private void processResult(Map<String, Coin> coinMap, List<HuobiCoinDepth> depthList) {
        if (depthList != null && !depthList.isEmpty()) {
            saveOrderBooks(createOrderBooks(coinMap, depthList));
        }
    }

    private void saveOrderBooks(Set<OrdersBook> ordersBookSet) {
        ordersBookRepository.saveAllAndFlush(ordersBookSet);
    }

    private static String generateSymbolsParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName().toLowerCase()).append("usdt").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
