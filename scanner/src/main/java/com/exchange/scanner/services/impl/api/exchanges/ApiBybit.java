package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bybit.chains.BybitChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.chains.BybitChainsRows;
import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.coins.BybitCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bybit.tickervolume.BybitCoinTickerList;
import com.exchange.scanner.dto.response.exchangedata.bybit.tickervolume.BybitCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee.BybitTradingFeeList;
import com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee.BybitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Bybit.BybitCoinDepthBuilder;
import com.exchange.scanner.services.utils.Bybit.BybitSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBybit implements ApiExchange {

    @Value("${exchanges.apiKeys.Bybit.key}")
    private String key;

    @Value("${exchanges.apiKeys.Bybit.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String NAME = "Bybit";

    public static final String BASE_ENDPOINT = "https://api.bybit.com";

    private static final int TIMEOUT = 10000;

    private static final String WSS_URL = "wss://stream.bybit.com/v5/public/spot";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private final WebClient webClient;

    public ApiBybit() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        BybitCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getResult() == null) return coins;

        coins = response.getResult().getList().stream()
            .filter(symbol -> symbol.getShowStatus().equals("1") && !symbol.getBaseCoin().endsWith("3S") &&
                    !symbol.getBaseCoin().endsWith("3L") && symbol.getQuoteCoin().equals("USDT"))
            .map(symbol -> {
                LinkDTO links = new LinkDTO();
                links.setDepositLink(exchange.getDepositLink());
                links.setWithdrawLink(exchange.getWithdrawLink());
                links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCoin().toUpperCase() + "/USDT/");
                return ObjectUtils.getCoin(symbol.getBaseCoin(), NAME, links, false);
            })
            .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BybitCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v3/public/symbols")
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
            .bodyToMono(BybitCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        BybitChainsResponse response = getChains().block();
        if (response == null || response.getResult() == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<BybitChainsRows> chainsRows = response.getResult().getRows().stream()
                .filter(row -> coinsNames.contains(row.getCoin()))
                .filter(row -> row.getChains().stream()
                        .allMatch(chain -> chain.getChainDeposit().equals("1") && chain.getChainWithdraw().equals("1"))
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();

            chainsRows.forEach(chainRow -> {
                if (chainRow.getCoin().equalsIgnoreCase(coin.getName())) {
                    chainRow.getChains().forEach(chainResponse -> {
                        String chainName = CoinChainUtils.unifyChainName(chainResponse.getChain().toUpperCase());
                        Chain chain = new Chain();
                        chain.setName(chainName);
                        chain.setCommission(new BigDecimal(chainResponse.getWithdrawFee()));
                        chain.setMinConfirm(Integer.valueOf(chainResponse.getConfirmation()));
                        chains.add(chain);
                    });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<BybitChainsResponse> getChains() {
        String timestamp = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
        String recv = "5000";
        String stringToSign = timestamp + key + recv;
        String sign = BybitSignatureBuilder.generateBybitSignature(stringToSign, secret);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v5/asset/coin/query-info")
                    .build()
            )
            .header("X-BAPI-SIGN", sign)
            .header("X-BAPI-API-KEY", key)
            .header("X-BAPI-TIMESTAMP", timestamp)
            .header("X-BAPI-RECV-WINDOW", recv)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BybitChainsResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        BybitTradingFeeResponse response = getFee().block();
        if (response == null || response.getResult() == null) return tradingFeeSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<BybitTradingFeeList> tradingFeeList = response.getResult().getList().stream()
                .filter(result -> symbols.contains(result.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            tradingFeeList.forEach(result -> {
                if (result.getSymbol().equalsIgnoreCase(coin.getName() + "USDT")) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            result.getTakerFeeRate()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Mono<BybitTradingFeeResponse> getFee() {
        String timestamp = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
        String recv = "5000";
        String paramStr = "category=spot";
        String stringToSign = timestamp + key + recv + paramStr;
        String sign = BybitSignatureBuilder.generateBybitSignature(stringToSign, secret);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v5/account/fee-rate")
                    .queryParam("category", "spot")
                    .build()
            )
            .header("X-BAPI-SIGN", sign)
            .header("X-BAPI-API-KEY", key)
            .header("X-BAPI-TIMESTAMP", timestamp)
            .header("X-BAPI-RECV-WINDOW", recv)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ".Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BybitTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        BybitCoinTickerVolume response = getCoinTickerVolume().block();
        if (response == null || response.getResult() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "USDT").toList();
        List<BybitCoinTickerList> tickerList = response.getResult().getList().stream()
                .filter(data -> symbols.contains(data.getS()))
                .toList();

        coins.forEach(coin -> {
            tickerList.forEach(data -> {
                if (data.getS().equalsIgnoreCase(coin.getName() + "USDT")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                            exchange,
                            coin,
                            data.getQv()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<BybitCoinTickerVolume> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/v3/public/quote/ticker/24hr")
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
            .bodyToMono(BybitCoinTickerVolume.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(Coin::getName, coin -> coin));
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
                Flux<Void> sendPingMessage = sendPingMessage(outbound);
                inbound.receive()
                    .asString()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> processTerminate(symbols, coinMap, client))
                    .onErrorResume(this::processError)
                    .map(this::processWebsocketResponse)
                    .filter(this::isValidResponseData)
                    .map(Optional::get)
                    .windowTimeout(coinMap.size(), Duration.ofSeconds(5))
                    .flatMap(Flux::collectList)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(depthList -> processResult(coinMap, depthList))
                    .subscribe();

                return outbound.then().thenMany(sendPingMessage);
            })
            .subscribe();
    }

    private void sendSubscribeMessage(List<String> symbols, WebsocketOutbound outbound) {
        List<List<String>> batches = ListUtils.partition(symbols, 10);

        Flux.fromIterable(batches).flatMap(batch -> outbound.sendString(Mono.just(createArgs(batch))))
            .delaySubscription(Duration.ofMillis(10))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private String createArgs(List<String> symbols) {
        StringBuilder args = new StringBuilder();
        symbols.forEach(symbol -> {
            args.append("\"").append("orderbook.50.").append(symbol).append("\"").append(",");
        });
        args.deleteCharAt(args.length() - 1);
        return "{" +
                "\"op\": " + "\"subscribe\"," +
                "\"args\": " + "[" + args + "]" +
                "}";
    }

    private Flux<Void> sendPingMessage(WebsocketOutbound outbound) {
        return Flux.interval(Duration.ofMinutes(5))
            .flatMap(tick -> outbound.send(Mono.just("{\"op\":\"ping\"}").then(Mono.empty())));
    }

    private void processTerminate(List<String> symbols, Map<String, Coin> coinMap, HttpClient client) {
        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
        reconnect(symbols, coinMap, client);
    }

    private void reconnect(List<String> symbols, Map<String, Coin> coinMap, HttpClient client) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
            .subscribe(aLong -> connect(symbols, coinMap, client));
    }

    private Mono<String> processError(Throwable error) {
        log.debug(error.getLocalizedMessage());
        return Mono.empty();
    }

    private Optional<BybitCoinDepth> processWebsocketResponse(String response) {
        if (response.contains("pong")) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(response, BybitCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<BybitCoinDepth> depth) {
        return depth.isPresent() &&
            depth.get().getData() != null &&
            depth.get().getData().getS() != null &&
            depth.get().getData().getA() != null && !depth.get().getData().getA().isEmpty() &&
            depth.get().getData().getB() != null && !depth.get().getData().getB().isEmpty();
    }

    private void processResult(Map<String, Coin> coinMap, List<BybitCoinDepth> depthList) {
        if (depthList != null && !depthList.isEmpty()) {
            saveOrderBooks(createOrderBooks(coinMap, depthList));
        }
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<BybitCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getData().getS().replaceAll("USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(BybitCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = BybitCoinDepthBuilder.getCoinDepth(currentCoin, depth.getData(), NAME);
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
