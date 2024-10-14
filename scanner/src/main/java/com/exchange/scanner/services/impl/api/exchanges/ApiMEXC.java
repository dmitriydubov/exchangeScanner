package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.mexc.chains.MexcChainResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.depth.MexcCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee.MexcTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.coins.MexcCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.mexc.tickervolume.MexcCoinTicker;
import com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee.MexcTradingFeeSymbol;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Mexc.MexcCoinDepthBuilder;
import com.exchange.scanner.services.utils.Mexc.MexcSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ApiMEXC implements ApiExchange {

    @Value("${exchanges.apiKeys.MEXC.key}")
    private String key;

    @Value("${exchanges.apiKeys.MEXC.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String NAME = "MEXC";

    public static final String BASE_HTTP_ENDPOINT = "https://api.mexc.com";

    private static final int HTTP_REQUEST_TIMEOUT = 10000;

    private static final String WSS_URL = "wss://wbs.mexc.com/ws";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    private final WebClient webClient;

    public ApiMEXC() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_HTTP_ENDPOINT, HTTP_REQUEST_TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        MexcCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getSymbols() == null) return coins;

        coins = response.getSymbols().stream()
                .filter(symbol -> symbol.getStatus().equals("1") &&
                        symbol.getQuoteAsset().equals("USDT") &&
                        symbol.getIsSpotTradingAllowed()
                )
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + symbol.getBaseAsset().toUpperCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + symbol.getBaseAsset().toUpperCase());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseAsset().toUpperCase() + "_USDT");
                    return ObjectUtils.getCoin(symbol.getBaseAsset(), NAME, links, symbol.getIsMarginTradingAllowed());
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<MexcCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/exchangeInfo")
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
            .bodyToMono(MexcCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<MexcChainResponse> response = getChainResponse().collectList().block();

        if (response == null || response.getFirst() == null) {
            log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            return chainsDTOSet;
        }
        Set<MexcChainResponse> filteredData = response.stream()
                .filter(data -> coinsNames.contains(data.getCoin()))
                .filter(data -> data.getNetworkList().stream()
                        .allMatch(network -> network.getDepositEnable() && network.getWithdrawEnable())
                )
                .collect(Collectors.toSet());

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            filteredData.forEach(data -> {
                if (coin.getName().equals(data.getCoin())) {
                    data.getNetworkList()
                        .forEach(networkList -> {
                            String chainName = CoinChainUtils.unifyChainName(networkList.getNetWork());
                            Chain chain = new Chain();
                            chain.setName(chainName);
                            chain.setCommission(new BigDecimal(networkList.getWithdrawFee()));
                            chain.setMinConfirm(networkList.getMinConfirm());
                            chains.add(chain);
                        });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Flux<MexcChainResponse> getChainResponse() {
        Map<String, String> params = new HashMap<>();
        String signature = MexcSignatureBuilder.generateMexcSignature(params, secret);
        params.put("signature", signature);

        return webClient
            .get()
            .uri(uriBuilder -> {
                uriBuilder.path("/api/v3/capital/config/getall");
                params.forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .header("X-MEXC-APIKEY", key)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(MexcChainResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        MexcTradingFeeResponse response = getFee().block();
        if (response == null || response.getSymbols().isEmpty()) return tradingFeeSet;

        List<String> coinsNames = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
        List<MexcTradingFeeSymbol> symbols = response.getSymbols().stream()
                .filter(symbol -> symbol.getQuoteAsset().equalsIgnoreCase("USDT") &&
                        coinsNames.contains(symbol.getSymbol())
                )
                .toList();

        coins.forEach(coin -> {
            symbols.forEach(symbol -> {
                if (symbol.getSymbol().equalsIgnoreCase(coin.getName() + "USDT")) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                            exchangeName,
                            coin,
                            symbol.getTakerCommission()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Mono<MexcTradingFeeResponse> getFee() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/exchangeInfo")
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
            .bodyToMono(MexcTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        List<MexcCoinTicker> response = getCoinTickerVolume().collectList().block();
        if (response == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
        List<MexcCoinTicker> filteredResponse = response.stream()
                .filter(data -> symbols.contains(data.getSymbol()))
                .toList();

        coins.forEach(coin -> {
            filteredResponse.forEach(data -> {
                if (data.getSymbol().equalsIgnoreCase(coin.getName() + "USDT")) {
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

    private Flux<MexcCoinTicker> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v3/ticker/24hr")
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
            .bodyToFlux(MexcCoinTicker.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "USDT").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toConcurrentMap(Coin::getName, coin -> coin));
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
                Flux<Void> sendPingMessage = sendPingMessage(outbound);
                sendSubscribeMessage(symbols, outbound);
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

    private Flux<Void> sendPingMessage(WebsocketOutbound outbound) {
        return Flux.interval(Duration.ofSeconds(30))
            .flatMap(tick -> outbound.send(Mono.just("{\"method\":\"PING\"}").then(Mono.empty())));
    }

    private void sendSubscribeMessage(List<String> symbols, WebsocketOutbound outbound) {
        List<List<String>> batches = ListUtils.partition(symbols, 20);
        Flux.fromIterable(batches).flatMap(batch -> outbound.sendString(Flux.fromIterable(createArgs(batch))))
            .delaySubscription(Duration.ofMillis(10))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private List<String> createArgs(List<String> symbols) {
        return symbols.stream()
            .map(symbol ->
                "{" +
                    "\"method\": " + "\"SUBSCRIPTION\"" + "," +
                    "\"params\": " + "[" + "\"" + "spot@public.limit.depth.v3.api@" + symbol + "@10" + "\"" + "]" + "," +
                    "\"id\": " + System.currentTimeMillis() +
                "}"
            )
            .toList();
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

    private Optional<MexcCoinDepth> processWebsocketResponse(String response) {
        if (response.contains("PONG")) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(response, MexcCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<MexcCoinDepth> coinDepth) {
        return coinDepth.isPresent() &&
            coinDepth.get().getD() != null &&
            coinDepth.get().getD().getAsks() != null &&
            coinDepth.get().getD().getBids() != null;
    }

    private void processResult(Map<String, Coin> coinMap, List<MexcCoinDepth> depthList) {
        if (depthList != null && !depthList.isEmpty()) {
            saveOrderBooks(createOrderBooks(coinMap, depthList));
        }
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<MexcCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getS().replaceAll("USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }
    
    private Optional<OrdersBook> getCurrentOrderBook(MexcCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = MexcCoinDepthBuilder.getCoinDepth(currentCoin, depth, NAME);
        OrdersBook ordersBook = OrdersBookUtils.createOrderBooks(coinDepth);

        if (ordersBook.getSlug() == null ||
                ordersBook.getBids().isEmpty() ||
                ordersBook.getAsks().isEmpty()) {
            return Optional.empty();
        }

        return ordersBookRepository.findBySlug(ordersBook.getSlug())
            .map(book -> OrdersBookUtils.updateOrderBooks(book, coinDepth))
            .or(() -> Optional.of(ordersBook));
    }

    private void saveOrderBooks(Set<OrdersBook> ordersBookSet) {
        ordersBookRepository.saveAllAndFlush(ordersBookSet);
    }
}
