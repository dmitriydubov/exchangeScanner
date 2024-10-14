package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.lbank.chains.LBankChainsData;
import com.exchange.scanner.dto.response.exchangedata.lbank.chains.LBankChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.lbank.depth.LBankCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.lbank.coins.LBankCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.lbank.tickervolume.LBankVolumeTickerData;
import com.exchange.scanner.dto.response.exchangedata.lbank.tickervolume.LBankVolumeTickerResponse;
import com.exchange.scanner.dto.response.exchangedata.lbank.tradingfee.LBankFeeData;
import com.exchange.scanner.dto.response.exchangedata.lbank.tradingfee.LBankTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.LBank.LBankCoinDepthBuilder;
import com.exchange.scanner.services.utils.LBank.LBankSignatureBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiLBank implements ApiExchange {

    @Value("${exchanges.apiKeys.LBank.key}")
    private String key;

    @Value("${exchanges.apiKeys.LBank.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersBookRepository ordersBookRepository;

    private static final String WSS_URL = "wss://www.lbkex.net/ws/V2/";

    private static final int MAX_WEBSOCKET_CONNECTION_RETRIES = 3;

    private static final Duration WEBSOCKET_RECONNECT_DELAY = Duration.ofSeconds(10);

    private static final String NAME = "LBank";

    public final static String BASE_ENDPOINT = "https://www.lbkex.net";

    private static final int TIMEOUT = 10000;

    private final WebClient webClient;

    public ApiLBank() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();

        LBankCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
            .filter(symbol -> symbol.getSymbol().endsWith("_usdt"))
            .map(symbol -> {
                String coinName = ObjectUtils.refactorToStandardCoinName(symbol.getSymbol(), "_");
                LinkDTO links = new LinkDTO();
                links.setDepositLink(exchange.getDepositLink() + coinName.toLowerCase());
                links.setWithdrawLink(exchange.getWithdrawLink() + coinName.toLowerCase());
                links.setTradeLink(exchange.getTradeLink() + coinName.toLowerCase() + "_usdt");
                return ObjectUtils.getCoin(coinName, NAME, links, false);
            })
            .collect(Collectors.toSet());

        return coins;
    }

    private Mono<LBankCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/accuracy.do")
                    .build()
            )
            .retrieve()
            .bodyToMono(LBankCurrencyResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        LBankChainsResponse response = getChains().block();
        if (response == null || response.getData() == null) return chainsDTOSet;
        List<String> coinsNames = coins.stream().map(Coin::getName).toList();
        List<LBankChainsData> chainsData = response.getData().stream()
            .filter(chainDTO -> coinsNames.contains(chainDTO.getAssetCode().toUpperCase()) &&
                    chainDTO.getCanWithdraw()
            )
            .toList();

        coins.forEach(coin -> {
            Set<Chain> chains = new HashSet<>();
            chainsData.forEach(data -> {
                if (data.getAssetCode().equalsIgnoreCase(coin.getName()) && data.getChain() != null) {
                    String chainName = CoinChainUtils.unifyChainName(data.getChain().toUpperCase());
                    Chain chain = new Chain();
                    chain.setName(chainName);
                    if (data.getFee() != null) {
                        chain.setCommission(new BigDecimal(data.getFee()));
                    } else {
                        chain.setCommission(new BigDecimal(BigInteger.ZERO));
                    }
                    chain.setMinConfirm(0);
                    chains.add(chain);
                }
            });

            ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
            chainsDTOSet.add(responseDTO);
        });

        return chainsDTOSet;
    }

    private Mono<LBankChainsResponse> getChains() {
        String requestPath = "/v2/withdrawConfigs.do";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .path(requestPath)
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
            .bodyToMono(LBankChainsResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();
        LBankTradingFeeResponse response = getFee().block();
        if (response == null) return tradingFeeSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "_usdt").toList();
        List<LBankFeeData> data = response.getData().stream()
            .filter(fee -> symbols.contains(fee.getSymbol()))
            .toList();

        coins.forEach(coin -> {
            data.forEach(fee -> {
                if (fee.getSymbol().equalsIgnoreCase(coin.getName().toLowerCase() + "_usdt")) {
                    TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        fee.getTakerCommission()
                    );
                    tradingFeeSet.add(responseDTO);
                }
            });
        });

        return tradingFeeSet;
    }

    private Mono<LBankTradingFeeResponse> getFee() {
        String requestPath = "/v2/supplement/customer_trade_fee.do";
        TreeMap<String, String> initialParams = new TreeMap<>();
        LBankSignatureBuilder signatureBuilder = new LBankSignatureBuilder(key, secret, initialParams);
        signatureBuilder.createSignature();
        TreeMap<String, String> params = signatureBuilder.getRequestParams();

        return webClient
            .post()
            .uri(uriBuilder -> {
                uriBuilder.path(requestPath);
                params.forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            })
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("echostr", signatureBuilder.getEchoStr())
            .header("signature_method", "HmacSHA256")
            .header("timestamp", signatureBuilder.getTimestamp())
            .retrieve()
            .onStatus(
                status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                    log.error("Ошибка получения торговой комиссии от " + NAME + ". Причина: {}", errorBody);
                    return Mono.empty();
                })
            )
            .bodyToMono(LBankTradingFeeResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();
        LBankVolumeTickerResponse response = getCoinTicker().block();
        if (response == null || response.getData() == null) return volume24HSet;
        List<String> symbols = coins.stream().map(coin -> coin.getName().toLowerCase() + "_usdt").toList();
        List<LBankVolumeTickerData> volumeData = response.getData().stream()
            .filter(data -> symbols.contains(data.getSymbol()))
            .toList();

        coins.forEach(coin -> {
            volumeData.forEach(data -> {
                if (data.getSymbol().equalsIgnoreCase(coin.getName() + "_usdt")) {
                    Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        data.getTicker().getTurnover()
                    );
                    volume24HSet.add(responseDTO);
                }
            });
        });

        return volume24HSet;
    }

    private Mono<LBankVolumeTickerResponse> getCoinTicker() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/ticker/24hr.do")
                .queryParam("symbol", "all")
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
            .bodyToMono(LBankVolumeTickerResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public void getOrderBook(Set<Coin> coins, String exchangek) {
        List<String> symbols = coins.stream().limit(20).map(coin -> coin.getName().toLowerCase() + "_usdt").toList();
        Map<String, Coin> coinMap = coins.stream().collect(Collectors.toMap(coin -> coin.getName().toLowerCase(), coin -> coin));
        HttpClient client = createClient();
        symbols.forEach(symbol -> createArgsAndConnectWebsocket(symbol, coinMap, client));
    }

    private HttpClient createClient() {
        return HttpClient.create()
                .keepAlive(true)
                .option(ChannelOption.SO_KEEPALIVE, true);
    }

    private void createArgsAndConnectWebsocket(String symbol, Map<String, Coin> coinMap, HttpClient client) {
        String payload = String.format(
            "{" +
                "\"action\":\"subscribe\"," +
                "\"subscribe\":\"depth\"," +
                "\"depth\":\"10\"," +
                "\"pair\":\"%s\"" +
            "}", symbol);

        connect(payload, coinMap, client);
    }

    private void connect(String payload, Map<String, Coin> coinMap, HttpClient client) {
        client.websocket()
            .uri(WSS_URL)
            .handle((inbound, outbound) -> {
                inbound.receive()
                    .asString()
                    .retryWhen(Retry.fixedDelay(MAX_WEBSOCKET_CONNECTION_RETRIES, WEBSOCKET_RECONNECT_DELAY))
                    .doOnTerminate(() -> {
                        log.error("Потеряно соединение с Websocket. Попытка повторного подключения...");
                        reconnect(payload, coinMap, client);
                    })
                    .onErrorResume(error -> {
                        log.error(error.getLocalizedMessage());
                        return Mono.empty();
                    })
                    .doOnNext(response -> {
                        if (response.contains("ping")) {
                            String pongResponse = String.format("{\"pong\":\"%s\",\"action\":\"pong\"}", extractPing(response));
                            outbound.sendString(Flux.just(pongResponse)).then().subscribe();
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
                            saveOrderBooks(createOrderBooks(coinMap, depthList));
                        }
                    })
                    .subscribe();

                return outbound.sendString(Mono.just(payload)).neverComplete();
            })
            .subscribe();
    }

    private void reconnect(String payload, Map<String, Coin> coinMap, HttpClient client) {
        Mono.delay(WEBSOCKET_RECONNECT_DELAY)
                .subscribe(aLong -> connect(payload, coinMap, client));
    }

    private String extractPing(String response) {
        return new JSONObject(response).getString("ping");
    }

    private Optional<LBankCoinDepth> processWebsocketResponse(String response) {
        try {
            return Optional.of(objectMapper.readValue(response, LBankCoinDepth.class));
        } catch (JsonProcessingException e) {
            log.info(e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean isValidResponseData(Optional<LBankCoinDepth> depth) {
        return depth.isPresent() &&
            depth.get().getDepth() != null &&
            depth.get().getDepth().getAsks() != null &&
            !depth.get().getDepth().getAsks().isEmpty() &&
            depth.get().getDepth().getBids() != null &&
            !depth.get().getDepth().getBids().isEmpty() &&
            depth.get().getPair() != null;
    }

    private Set<OrdersBook> createOrderBooks(Map<String, Coin> coinMap, List<LBankCoinDepth> depthList) {
        return depthList.stream().map(depth -> {
                Coin currentCoin = coinMap.get(depth.getPair().replaceAll("_usdt", ""));
                return getCurrentOrderBook(depth, currentCoin);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<OrdersBook> getCurrentOrderBook(LBankCoinDepth depth, Coin currentCoin) {
        CoinDepth coinDepth = LBankCoinDepthBuilder.getCoinDepth(currentCoin, depth.getDepth(), NAME);
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
