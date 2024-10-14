package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.xt.chains.XTChainResponse;
import com.exchange.scanner.dto.response.exchangedata.xt.chains.XTChainResult;
import com.exchange.scanner.dto.response.exchangedata.xt.depth.XTCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.xt.coins.XTCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.xt.tickervolume.XTVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.xt.tickervolume.XTVolumeTickerResult;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.XT.XTCoinDepthBuilder;
import com.exchange.scanner.services.utils.XT.XTSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiXT implements ApiExchange {

    @Value("${exchanges.apiKeys.XT.key}")
    private String key;

    @Value("${exchanges.apiKeys.XT.secret}")
    private String secret;

    private static final String NAME = "XT";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://stream.xt.com/public";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    public final static String BASE_ENDPOINT = "https://sapi.xt.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiXT() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        XTCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getResult() == null) return coins;

        coins = response.getResult().getSymbols().stream()
                .filter(symbol ->
                    symbol.getQuoteCurrency().equals("usdt") &&
                    !symbol.getBaseCurrency().endsWith("3s") && !symbol.getBaseCurrency().endsWith("3l") &&
                    symbol.getTradingEnabled() &&
                    symbol.getState().equals("ONLINE")
                )
                .map(symbol -> {
                    String coinName = symbol.getBaseCurrency().toUpperCase();
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + coinName.toLowerCase() + "_usdt");
                    return ObjectUtils.getCoin(coinName, NAME, links, false);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<XTCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v4/public/symbol")
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
            .bodyToMono(XTCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();

        XTChainResponse response = getChains().block();

        if (response == null || response.getResult() == null) return chainsDTOSet;

        Set<String> coinsNames = coins.stream()
                .map(Coin::getName)
                .collect(Collectors.toSet());

        List<XTChainResult> xtChainResultListFiltered = response.getResult().stream()
                .filter(result -> coinsNames.contains(result.getCurrency().toUpperCase()))
                .filter(result -> result.getSupportChains().stream()
                        .allMatch(chain -> chain.getWithdrawEnabled() && chain.getDepositEnabled())
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            xtChainResultListFiltered.forEach(result -> {
                if (coin.getName().equals(result.getCurrency().toUpperCase())) {
                    result.getSupportChains()
                        .forEach(chainResponse -> {
                            String chainName = CoinChainUtils.unifyChainName(chainResponse.getChain());
                            Chain chain = new Chain();
                            chain.setName(chainName.toUpperCase());
                            chain.setCommission(new BigDecimal(chainResponse.getWithdrawFeeAmount()));
                            chain.setMinConfirm(0);
                            chains.add(chain);
                        });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<XTChainResponse> getChains() {
        String requestPath = "/v4/public/wallet/support/currency";
        TreeMap<String, String> params = new TreeMap<>();
        XTSignatureBuilder signatureBuilder = new XTSignatureBuilder(key, secret, params);
        signatureBuilder.createSignature("GET", requestPath);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .build()
            )
            .headers(httpHeaders -> signatureBuilder.getHeaders().forEach(httpHeaders::add))
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(XTChainResponse.class)
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
                    "0.002"
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        XTVolumeTicker response = getCoinTickerVolume().block();
        if (response == null || response.getResult() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "_usdt").toList();
        List<XTVolumeTickerResult> volumeTicker = response.getResult().stream()
                .filter(data -> symbols.contains(data.getS()))
                .toList();

        coins.forEach(coin -> {
           volumeTicker.forEach(ticker -> {
               if (ticker.getS().equalsIgnoreCase(coin.getName() + "_usdt")) {
                   Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                           exchange,
                           coin,
                           ticker.getV()
                   );
                   volume24HSet.add(responseDTO);
               }
           });
        });

        return volume24HSet;
    }

    private Mono<XTVolumeTicker> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/v4/public/ticker")
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
            .bodyToMono(XTVolumeTicker.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "_usdt").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(coin -> coin.getName().toLowerCase(), coin -> coin));
        String id = String.valueOf(UUID.randomUUID());
        WebSocketClient client = new ReactorNettyWebSocketClient();
        connect(symbols, coinMap, id, client);
    }

    private String createArgs(List<String> symbols, String id) {
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> args.append("\"").append("depth_update@").append(symbol).append("\"").append(","));
        args.deleteCharAt(args.length() - 1);

        return String.format(
            "{ " +
                "\"method\": \"subscribe\"," +
                "\"params\": [%s]," +
                "\"id\": \"%s\"" +
            " }", args, id);
    }

    private void connect(List<String> symbols, Map<String, Coin> coinMap, String id, WebSocketClient client) {
        Hooks.onErrorDropped(error -> log.error(error.getLocalizedMessage()));

        client.execute(URI.create(WSS_URL), session -> {
            session.receive()
                .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                .doOnTerminate(() -> processTerminate(symbols, coinMap, id, client))
                .onErrorResume(this::processError)
                .map(this::processWebsocketResponse)
                .filter(this::isValidResponseData)
                .map(Optional::get)
                .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                .flatMap(Flux::collectList)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(depthList -> processResult(coinMap, depthList))
                .subscribe();

            String payload = createArgs(symbols, id);
            WebSocketMessage message = session.textMessage(payload);
            return session.send(Mono.just(message)).then(sendPingFlux(session));
        })
        .subscribe();
    }

    private Mono<Void> sendPingFlux(WebSocketSession session) {
        return Flux.interval(Duration.ofSeconds(5))
            .flatMap(tick -> {
                WebSocketMessage message = session.textMessage("ping");
                return session.send(Mono.just(message));
            }).then();
    }

    private void processTerminate(List<String> symbols, Map<String, Coin> coinMap, String id, WebSocketClient client) {
        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
        reconnect(symbols, coinMap, id, client);
    }

    private void reconnect(List<String> symbols, Map<String, Coin> coinMap, String id, WebSocketClient client) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
            .subscribe(aLong -> connect(symbols, coinMap, id, client));
    }

    private Mono<WebSocketMessage> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private Optional<XTCoinDepth> processWebsocketResponse(WebSocketMessage response) {
        if (response.getPayloadAsText().contains("pong")) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(response.getPayloadAsText(), XTCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getLocalizedMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<XTCoinDepth> coinDepth) {
        return coinDepth.isPresent() &&
            coinDepth.get().getData() != null &&
            coinDepth.get().getData().getB() != null && !coinDepth.get().getData().getB().isEmpty() &&
            coinDepth.get().getData().getA() != null && !coinDepth.get().getData().getA().isEmpty();
    }

    private void processResult(Map<String, Coin> coinMap, List<XTCoinDepth> depthList) {
        if (depthList != null && !depthList.isEmpty()) {
            saveOrderBooks(createOrderBooks(coinMap, depthList));
        }
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<XTCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getData().getS().replaceAll("_usdt", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(XTCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = XTCoinDepthBuilder.getCoinDepth(currentCoin, depth.getData(), NAME);
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
