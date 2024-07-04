package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.huobi.chains.HuobiChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.huobi.exchangeinfo.HuobiSymbolData;
import com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume.HuobiVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiFeeData;
import com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee.HuobiTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huobi.client.TradeClient;
import com.huobi.client.req.trade.FeeRateRequest;
import com.huobi.constant.HuobiOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiHuobi implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${exchanges.apiKeys.Huobi.key}")
    private String key;

    @Value("${exchanges.apiKeys.Huobi.secret}")
    private String secret;

    private static final String NAME = "Huobi";

    public final static String BASE_ENDPOINT = "https://api.huobi.pro";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

    private static final String AGGREGATION_LEVEL_TYPE = "step0";

    private final WebClient webClient;

    public ApiHuobi() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/v2/settings/common/symbols";

        ResponseEntity<HuobiSymbolData> responseEntity = restTemplate.getForEntity(url, HuobiSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Huobi, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Huobi, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getQcdn().equals("USDT") && symbol.getTe())
                .map(symbol -> CoinFactory.getCoin(symbol.getBcdn()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Coin> getCoinChain(Set<Coin> coins) {
        Set<Coin> coinsWithChains = new HashSet<>();

        coins.forEach(coin -> {
            HuobiChainsResponse response = getChains(coin).block();

            if (response != null) {
                Set<Chain> chains = new HashSet<>();
                response.getData().getFirst().getChains().forEach(chainResponse -> {
                    Chain chain = new Chain();
                    chain.setName(chainResponse.getDisplayName());
                    chain.setCommission(new BigDecimal(chainResponse.getTransactFeeWithdraw()));
                    chains.add(chain);
                });
                coin.setChains(chains);
                coinsWithChains.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithChains;
    }

    private Mono<HuobiChainsResponse> getChains(Coin coin) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/reference/currencies")
                        .queryParam("currency", coin.getName().toLowerCase())
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
                .bodyToMono(HuobiChainsResponse.class);
    }

    @Override
    public Set<Coin> getTradingFee(Set<Coin> coins) {
        Set<Coin> coinsWithTradingFee = new HashSet<>();

        HuobiTradingFeeResponse response = getFee(coins);

        Set<String> symbols = coins.stream()
                .map(coin -> coin.getName().toLowerCase() + "usdt")
                .collect(Collectors.toSet());
        List<HuobiFeeData> data = response.getData().stream()
                .filter(dataResponse -> symbols.contains(dataResponse.getSymbol()))
                .toList();
        coins.forEach(coin -> {
            data.forEach(feeResponse -> {
                if (coin.getName().equalsIgnoreCase(feeResponse.getSymbol().replaceAll("usdt", ""))) {
                    coin.setTakerFee(new BigDecimal(feeResponse.getTakerFeeRate()));
                    coinsWithTradingFee.add(coin);
                }
            });
        });

        return coinsWithTradingFee;
    }

    private HuobiTradingFeeResponse getFee(Set<Coin> coins) {
        HuobiTradingFeeResponse response = new HuobiTradingFeeResponse();
        List<HuobiFeeData> data = new ArrayList<>();
        int requestSymbolMaxSize = 20;
        List<List<Coin>> partitions = ListUtils.partition(new ArrayList<>(coins), requestSymbolMaxSize);
        TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                .apiKey(key)
                .secretKey(secret)
                .build());
        partitions.forEach(partition -> {
            FeeRateRequest request = new FeeRateRequest(generateSymbolsParameters(partition));
            tradeService.getFeeRate(request).forEach(feeRate -> {
                HuobiFeeData huobiFeeData = new HuobiFeeData();
                huobiFeeData.setSymbol(feeRate.getSymbol());
                huobiFeeData.setTakerFeeRate(String.valueOf(feeRate.getTakerFeeRate()));
                data.add(huobiFeeData);
            });
        });
        response.setData(data);
        return response;
    }

    @Override
    public Set<Coin> getCoinVolume24h(Set<Coin> coins) {
        Set<Coin> coinsWithVolume24h = new HashSet<>();

        coins.forEach(coin -> {
            HuobiVolumeTicker response = getCoinTickerVolume(coin).block();

            if (response != null) {
                coin.setVolume24h(new BigDecimal(response.getTick().getVol()));
                coinsWithVolume24h.add(coin);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinsWithVolume24h;
    }

    private Mono<HuobiVolumeTicker> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName().toLowerCase() + "usdt";

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/market/detail/merged")
                        .queryParam("symbol", symbol)
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
                .bodyToMono(HuobiVolumeTicker.class);
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<String> coins) {
        Flux<CoinDepth> response = getCoinDepth(coins);

        return new HashSet<>(Objects.requireNonNull(response
                .collectList()
                .block()));
    }

    private Flux<CoinDepth> getCoinDepth(Set<String> coins) {
        List<String> coinSymbols = coins.stream().map(coin -> coin.toLowerCase() + "usdt").toList();

        return Flux.fromIterable(coinSymbols)
                .delayElements(Duration.ofMillis(REQUEST_DELAY_DURATION))
                .flatMap(coin -> webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/market/depth")
                                .queryParam("symbol", coin)
//                                .queryParam("depth", DEPTH_REQUEST_LIMIT)
                                .queryParam("type", AGGREGATION_LEVEL_TYPE)
                                .build()
                        )
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                                    log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                                    return Mono.empty();
                                })
                        )
                        .bodyToFlux(String.class)
                        .map(response -> {
                            try {
                                HuobiCoinDepth huobiCoinDepth = objectMapper.readValue(response, HuobiCoinDepth.class);
                                huobiCoinDepth.setCoinName(coin.replaceAll("usdt", "").toUpperCase());

                                return ApiExchangeUtils.getHuobiCoinDepth(huobiCoinDepth);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
    }

    private static String generateSymbolsParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getSymbol().toLowerCase()).append("usdt").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
