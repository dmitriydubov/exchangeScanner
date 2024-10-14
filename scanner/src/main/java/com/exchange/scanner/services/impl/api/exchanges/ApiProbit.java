package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.probit.chains.Data;
import com.exchange.scanner.dto.response.exchangedata.probit.chains.ProbitChainData;
import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.probit.tickervolume.ProbitTickerData;
import com.exchange.scanner.dto.response.exchangedata.probit.tradingfee.FeeData;
import com.exchange.scanner.dto.response.exchangedata.probit.tradingfee.ProbitTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.probit.coins.ProbitCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.probit.tickervolume.ProbitTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Probit.ProbitCoinDepthBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiProbit implements ApiExchange {

    private static final String NAME = "Probit";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://api.probit.com/api/exchange/v1/ws";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(20);

    public final static String BASE_ENDPOINT = "https://api.probit.com/api/exchange/v1";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiProbit() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        ProbitCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
            .filter(symbol ->
                    symbol.getQuoteCurrencyId().equals("USDT") && !symbol.getClosed()
            )
            .map(symbol -> {
                LinkDTO links = new LinkDTO();
                links.setDepositLink(exchange.getDepositLink() + symbol.getBaseCurrencyId().toUpperCase());
                links.setWithdrawLink(exchange.getWithdrawLink() + symbol.getBaseCurrencyId().toUpperCase());
                links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCurrencyId().toUpperCase() + "-USDT");
                return ObjectUtils.getCoin(symbol.getBaseCurrencyId(), NAME, links, false);
            })
            .collect(Collectors.toSet());

        return coins;
    }

    private Mono<ProbitCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/market")
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
            .bodyToMono(ProbitCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        Set<String> coinsNames = coins.stream().map(Coin::getName).collect(Collectors.toSet());

        ProbitChainData response = getChainResponse().block();
        if (response == null || response.getData() == null) {
            log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            return chainsDTOSet;
        }
        List<Data> data = response.getData().stream()
                .filter(coinResponse -> coinsNames.contains(coinResponse.getId()) &&
                        !coinResponse.getDepositSuspended() &&
                        !coinResponse.getWithdrawalSuspended()
                )
                .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            data.forEach(dtoResponseElement -> {
                if (coin.getName().equals(dtoResponseElement.getId())) {
                    dtoResponseElement.getWithdrawalFee().forEach(chainsDto -> {
                        String chainName = CoinChainUtils.unifyChainName(dtoResponseElement.getPlatform().toUpperCase());
                        Chain chain = new Chain();
                        chain.setName(chainName);
                        chain.setCommission(new BigDecimal(chainsDto.getAmount().trim()));
                        chain.setMinConfirm(dtoResponseElement.getMinConfirmationCount());
                        chains.add(chain);
                    });
                }
            });
            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<ProbitChainData> getChainResponse() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/currency")
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
            .bodyToMono(ProbitChainData.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        Set<String> symbols = coins.stream().map(coin -> coin.getName() + "-USDT").collect(Collectors.toSet());

        ProbitTradingFeeResponse response = getFee().block();

        if (response == null || response.getData() == null) return tradingFeeSet;
        List<FeeData> data = response.getData().stream()
                .filter(feeData -> symbols.contains(feeData.getId()))
                .toList();

        coins.forEach(coin -> data.forEach(feeData -> {
            if (coin.getName().equals(feeData.getBaseCurrencyId())) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        feeData.getTakerFeeRate()
                );
                tradingFeeSet.add(responseDTO);
            }
        }));

        return tradingFeeSet;
    }

    private Mono<ProbitTradingFeeResponse> getFee() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/market")
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
            .bodyToMono(ProbitTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        ProbitTickerVolume response = getCoinTickerVolume().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName() + "-USDT").toList();
        List<ProbitTickerData> ticker = response.getData().stream()
                .filter(data -> symbols.contains(data.getMarketId()))
                .toList();

        coins.forEach(coin -> ticker.forEach(data -> {
            if (data.getMarketId().equalsIgnoreCase(coin.getName() + "-USDT")) {
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

    private Mono<ProbitTickerVolume> getCoinTickerVolume() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/ticker")
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
            .bodyToMono(ProbitTickerVolume.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchange) {
        List<String> symbols = coins.stream().map(coin -> coin.getName().toUpperCase() + "-USDT").toList();
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

        client.websocket(WebsocketClientSpec.builder()
            .maxFramePayloadLength(1048576)
            .build())
            .uri(WSS_URL)
            .handle((inbound, outbound) -> {
                sendSubscribeMessage(symbols, outbound);
                inbound.receive()
                    .asString()
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

                return outbound.neverComplete();
            })
            .subscribe();
    }

    private void sendSubscribeMessage(List<String> symbols, WebsocketOutbound outbound) {
        Flux.fromIterable(symbols).flatMap(symbol -> outbound.sendString(Mono.just(createArgs(symbol))))
            .delaySubscription(Duration.ofMillis(10))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private String createArgs(String symbol) {
        return String.format(
            "{ " +
                "\"channel\": \"marketdata\", " +
                "\"filter\": [\"order_books_l1\"], " +
                "\"interval\": 100, " +
                "\"market_id\": \"%s\", " +
                "\"type\": \"subscribe\" " +
            "}", symbol);
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

    private Optional<ProbitCoinDepth> processWebsocketResponse(String response) {
        try {
            return Optional.of(objectMapper.readValue(response, ProbitCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<ProbitCoinDepth> coinDepth) {
        return coinDepth.isPresent() &&
            coinDepth.get().getOrderBooksL1() != null &&
            !coinDepth.get().getOrderBooksL1().isEmpty();
    }

    private void processResult(Map<String, Coin> coinMap, List<ProbitCoinDepth> depthList) {
        if (depthList != null && !depthList.isEmpty()) {
            saveOrderBooks(createOrderBooks(coinMap, depthList));
        }
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<ProbitCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getMarketId().replaceAll("-USDT", ""));
                if (currentCoin == null) return Optional.<OrdersBook>empty();
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(ProbitCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = ProbitCoinDepthBuilder.getCoinDepth(currentCoin, depth.getOrderBooksL1(), NAME);
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
