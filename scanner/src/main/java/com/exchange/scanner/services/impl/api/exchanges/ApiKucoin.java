package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.websocket.PublicKeyForWebsocket;
import com.exchange.scanner.dto.response.exchangedata.kucoin.chains.KucoinChainData;
import com.exchange.scanner.dto.response.exchangedata.kucoin.chains.KucoinChainResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume.KucoinTicker;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tradingfee.KucoinTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.coins.KucoinCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume.KucoinTickerVolumeResponse;
import com.exchange.scanner.dto.response.exchangedata.kucoin.websocket.WebsocketPublicKeyResponseDTO;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Kucoin.KucoinCoinDepthBuilder;
import com.exchange.scanner.services.utils.Kucoin.KucoinSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiKucoin implements ApiExchange {

    @Value("${exchanges.apiKeys.Kucoin.key}")
    private String key;

    @Value("${exchanges.apiKeys.Kucoin.secret}")
    private String secret;

    @Value("${exchanges.apiKeys.Kucoin.passphrase}")
    private String passphrase;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private static final String NAME = "Kucoin";

    public final static String BASE_ENDPOINT = "https://api.kucoin.com";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiKucoin() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();
        KucoinCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
            .filter(currency -> currency.getQuoteCurrency().equals("USDT") && !currency.getBaseCurrency().endsWith("3S") &&
                    !currency.getBaseCurrency().endsWith("3L") && currency.getEnableTrading())
            .map(currency -> {
                LinkDTO links = new LinkDTO();
                links.setDepositLink(exchange.getDepositLink() + currency.getBaseCurrency().toUpperCase());
                links.setWithdrawLink(exchange.getWithdrawLink() + currency.getBaseCurrency().toUpperCase());
                links.setTradeLink(exchange.getTradeLink() + currency.getBaseCurrency().toUpperCase() + "-USDT");
                return ObjectUtils.getCoin(currency.getBaseCurrency(), NAME, links, currency.getIsMarginEnabled());
            })
            .collect(Collectors.toSet());

        return coins;
    }

    private Mono<KucoinCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/symbols")
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
            .bodyToMono(KucoinCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        KucoinChainResponse response = getChain().block();
        if (response == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<KucoinChainData> chainData = response.getData().stream()
                .filter(data -> coinsNames.contains(data.getCurrency()))
                .filter(data -> data.getChains().stream()
                        .allMatch(chain -> chain.getIsDepositEnabled() && chain.getIsWithdrawEnabled())
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            chainData.forEach(data -> {
                if (data.getCurrency().equalsIgnoreCase(coin.getName())) {
                    data.getChains().forEach(chainDTO -> {
                        String chainName = CoinChainUtils.unifyChainName(chainDTO.getChainName());
                        Chain chain = new Chain();
                        chain.setName(chainName);
                        chain.setCommission(new BigDecimal(chainDTO.getWithdrawalMinFee()));
                        chain.setMinConfirm(chainDTO.getConfirms());
                        chains.add(chain);
                    });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<KucoinChainResponse> getChain() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/currencies")
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
            .bodyToMono(KucoinChainResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        KucoinTradingFeeResponse response = getFee().blockLast();

        if (response == null || response.getData() == null) return tradingFeeSet;

        coins.forEach(coin -> {
            TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                    exchangeName,
                    coin,
                    response.getData().getTakerFeeRate()
            );
            tradingFeeSet.add(responseDTO);
        });

        return tradingFeeSet;
    }

    private Flux<KucoinTradingFeeResponse> getFee() {
        String endpoint = "/api/v1/base-fee";

        String timestamp = String.valueOf(System.currentTimeMillis());
        String strToSign = timestamp + "GET" + endpoint;
        String signature = KucoinSignatureBuilder.generateKucoinSignature(secret, strToSign);
        String encodedPassphrase = KucoinSignatureBuilder.generateKucoinPassphrase(secret, passphrase);
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(endpoint)
                    .build()
            )
            .header("KC-API-KEY", key)
            .header("KC-API-SIGN", signature)
            .header("KC-API-TIMESTAMP", timestamp)
            .header("KC-API-PASSPHRASE", encodedPassphrase)
            .header("KC-API-KEY-VERSION", "3")
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговых комиссии от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(KucoinTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        KucoinTickerVolumeResponse response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "-USDT").toList();
        List<KucoinTicker> ticker = response.getData().getTicker().stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            ticker.forEach(data -> {
                if (data.getSymbol().equalsIgnoreCase(coin.getName() + "-USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getVolValue()
                    );

                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<KucoinTickerVolumeResponse> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/market/allTickers")
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
            .bodyToMono(KucoinTickerVolumeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange, BlockingDeque<Runnable> taskQueue, ReentrantLock lock) {
        Optional<PublicKeyForWebsocket> optionalPublicToken = Optional.ofNullable(getPublicToken().block());
        if (optionalPublicToken.isEmpty()) {
            log.debug("отсутствует публичный токен подключения");
            return;
        }
        String connectId = UUID.randomUUID().toString();
        String url = optionalPublicToken.get().url() + "?token=" +
                optionalPublicToken.get().token() + "&[connectId=" + connectId + "]";
        boolean isConnectionOk = checkWebsocketConnection(url);
        if (!isConnectionOk) {
            log.debug("ошибка подключения");
            return;
        }
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "-USDT").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(Coin::getName, coin -> coin));

        HttpClient client = createClient();
        connect(symbols, coinMap, url, taskQueue, client, lock);
    }

    private Mono<PublicKeyForWebsocket> getPublicToken() {
        return webClient.post()
            .uri("/api/v1/bullet-public")
            .retrieve()
            .bodyToMono(WebsocketPublicKeyResponseDTO.class)
            .onErrorResume(error -> {
                log.debug(error.getLocalizedMessage());
                return Mono.empty();
            })
            .flatMap(response -> {
                String token = response.getData().getToken();
                String wssUrl = response.getData().getInstanceServers().getFirst().getEndpoint();
                return Mono.just(new PublicKeyForWebsocket(wssUrl, token));
            });
    }

    private Boolean checkWebsocketConnection(String url) {
        return HttpClient.create()
            .websocket()
            .uri(url)
            .handle((inbound, outbound) -> inbound.receive()
                .asString()
                .flatMap(response -> Mono.just(response.contains("welcome"))))
                .blockFirst();
    }

    private HttpClient createClient() {
        return HttpClient.create()
                .keepAlive(true)
                .option(ChannelOption.SO_KEEPALIVE, true);
    }

    private void connect(
        List<String> symbols,
        Map<String, Coin> coinMap,
        String url,
        BlockingDeque<Runnable> taskQueue,
        HttpClient client,
        ReentrantLock lock
    ) {
        Hooks.onErrorDropped(error -> log.error(error.getLocalizedMessage()));

        client.websocket(WebsocketClientSpec.builder()
            .build())
            .uri(url)
            .handle((inbound, outbound) -> {
                List<List<String>> batches = ListUtils.partition(symbols, 20);
                List<String> args = batches.stream().map(this::createArgs).toList();
                sendSubscribeMessage(args, outbound);
                Flux<Void> pingFlux = sendPingFlux(outbound, new JSONObject(args.getFirst()).getString("id"));
                inbound.receive()
                    .asString()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> processTerminate(symbols, coinMap, url, taskQueue, client, lock))
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

    private void sendSubscribeMessage(List<String> args, WebsocketOutbound outbound) {
        Flux.fromIterable(args).flatMap(payload -> outbound.sendString(Mono.just(payload)))
            .delaySubscription(Duration.ofMillis(10))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private String createArgs(List<String> symbols) {
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> args.append(symbol).append(","));
        args.deleteCharAt(args.length() - 1);
        String id = String.valueOf(new Random().nextLong(1_000_000, 10_000_000));
        return String.format(
            "{" +
                "\"id\": \"%s\"," +
                "\"type\": \"subscribe\"," +
                "\"topic\": \"/spotMarket/level2Depth50:%s\"," +
                "\"privateChannel\": false," +
                "\"response\": true" +
            "}", id, args);
    }

    private Flux<Void> sendPingFlux(WebsocketOutbound outbound, String id) {
        return Flux.interval(Duration.ofSeconds(20))
            .flatMap(tick -> {
                String pingMessage = String.format(
                    "{" +
                        "\"id\": \"%s\"," +
                        "\"type\": \"ping\"" +
                    "}", id);
                return outbound.sendString(Mono.just(pingMessage)).then(Mono.empty());
            }).onErrorResume(error -> {
                log.debug(error.getLocalizedMessage());
                return Mono.empty();
            });
    }

    private void processTerminate(
        List<String> symbols,
        Map<String, Coin> coinMap,
        String url,
        BlockingDeque<Runnable> taskQueue,
        HttpClient client,
        ReentrantLock lock
    ) {
        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
        reconnect(symbols, coinMap, url, taskQueue, client, lock);
    }

    private void reconnect(
        List<String> symbols,
        Map<String, Coin> coinMap,
        String url,
        BlockingDeque<Runnable> taskQueue,
        HttpClient client,
        ReentrantLock lock
    ) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
            .subscribe(aLong -> connect(symbols, coinMap, url, taskQueue, client, lock));
    }

    private Mono<String> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private Optional<KucoinCoinDepth> processWebsocketResponse(String response) {
        if (response.contains("pong")) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(response, KucoinCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<KucoinCoinDepth> coinDepth) {
        return coinDepth.isPresent() && coinDepth.get().getTopic() != null &&
            coinDepth.get().getData().getBids() != null && !coinDepth.get().getData().getBids().isEmpty() &&
            coinDepth.get().getData().getAsks() != null && !coinDepth.get().getData().getAsks().isEmpty();
    }

    private void processResult(
            Map<String, Coin> coinMap, BlockingDeque<Runnable> taskQueue, List<KucoinCoinDepth> depthList, ReentrantLock lock
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

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<KucoinCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getTopic()
                    .replaceAll("/spotMarket/level2Depth50:", "")
                    .replaceAll("-USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(KucoinCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = KucoinCoinDepthBuilder.getCoinDepth(currentCoin, depth.getData(), NAME);
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
