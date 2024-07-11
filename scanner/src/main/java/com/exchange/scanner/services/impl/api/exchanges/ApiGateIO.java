package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.gateio.chains.ChainDTO;
import com.exchange.scanner.dto.response.exchangedata.gateio.coins.GateIoCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.gateio.depth.GateIOCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.gateio.volume24h.GateIOCoinTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.gateio.tradingfee.GateIOTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.GateIO.GateIOCoinDepthBuilder;
import com.exchange.scanner.services.utils.GateIO.GateIOSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiGateIO implements ApiExchange {

    @Value("${exchanges.apiKeys.GateIO.key}")
    private String key;

    @Value("${exchanges.apiKeys.GateIO.secret}")
    private String secret;

    private static final String NAME = "Gate.io";

    private final static String BASE_ENDPOINT = "https://api.gateio.ws/api/v4";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiGateIO() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        Set<Coin> coins = new HashSet<>();
        List<GateIoCurrencyResponse> response = getCurrencies().collectList().block();
        if (response == null || response.getFirst() == null) return coins;

        coins = response.stream()
                .filter(currency -> currency.getQuote().equals("USDT") && currency.getTradeStatus().equals("tradable"))
                .map(currency -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink() + currency.getBase().toUpperCase());
                    links.setWithdrawLink(exchange.getWithdrawLink() + currency.getBase().toUpperCase());
                    links.setTradeLink(exchange.getTradeLink() + currency.getBase().toUpperCase() + "_USDT");
                    return ObjectUtils.getCoin(currency.getBase(), NAME, links);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Flux<GateIoCurrencyResponse> getCurrencies() {

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/currency_pairs")
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
            .bodyToFlux(GateIoCurrencyResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        coins.forEach(coin -> {
            List<Chain> response = getChains(coin).collectList().block();

            if (response != null && response.getFirst() != null) {
                Set<Chain> chains = new HashSet<>(response);
                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            } else {
                log.error("При попытке получения списка сетей получен пустой ответ от {}", NAME);
            }
            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        return chainsDTOSet;
    }

    private Flux<Chain> getChains(Coin coin) {
        String endpoint = "/wallet/currency_chains";
        String requestUrl = BASE_ENDPOINT + endpoint + "?currency" + coin.getName();
        String signature =  GateIOSignatureBuilder.generateGateIOSignature(secret, requestUrl);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(endpoint)
                    .queryParam("currency", coin.getName())
                    .build()
            )
            .header("KEY", key)
            .header("SIGN", signature)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения списка сетей от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToFlux(ChainDTO.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            })
            .filter(chainDTO -> chainDTO.getIsDisabled() == 0 && chainDTO.getIsDepositDisabled() == 0 && chainDTO.getIsWithdrawDisabled() == 0)
            .map(chainDTO -> {
                Chain chain = new Chain();
                chain.setName(chainDTO.getChain());
                chain.setCommission(new BigDecimal("0"));
                return chain;
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            GateIOTradingFeeResponse response = getFee(coin).block();

            if (response != null && response.getTakerFee() != null) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        response.getTakerFee()
                );
                tradingFeeSet.add(responseDTO);
            } else {
                log.error("При попытке получения торговой комиссии для монеты {}, получен пустой ответ от {}", coin.getName(), NAME);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return tradingFeeSet;
    }

    private Mono<GateIOTradingFeeResponse> getFee(Coin coin) {
        String endpoint = "/api/v4/wallet/fee";
        String symbol = coin.getName() + "_USDT";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        String method = "GET";
        String queryString = "currency_pair=" + symbol;
        String requestBody = "";
        String payloadHash = GateIOSignatureBuilder.hashSHA512(requestBody);
        String data = method.toUpperCase() + "\n" +
                endpoint.trim() + "\n" +
                queryString.trim() + "\n" +
                payloadHash.trim() + "\n" +
                timestamp.trim();

        String signature = GateIOSignatureBuilder.generateGateIOSignature(secret.trim(), data.trim());

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/wallet/fee")
                    .queryParam("currency_pair", symbol)
                    .build()
            )
            .header("KEY", key)
            .header("Timestamp", timestamp)
            .header("SIGN", signature)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(GateIOTradingFeeResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            List<GateIOCoinTickerVolume> response = getCoinTickerVolume(coin).collectList().block();

            if (response != null && response.getFirst() != null) {
                GateIOCoinTickerVolume volume = response.getFirst();
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        volume.getQuoteVolume()
                );

                volume24HSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return volume24HSet;
    }

    private Flux<GateIOCoinTickerVolume> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/tickers")
                    .queryParam("currency_pair", symbol)
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
            .bodyToFlux(GateIOCoinTickerVolume.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Flux.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();
        coins.forEach(coin -> {
            GateIOCoinDepth response = getCoinDepth(coin).block();

            if (response != null && (response.getBids() != null || response.getAsks() != null)) {
                CoinDepth coinDepth = GateIOCoinDepthBuilder.getCoinDepth(coin, response, exchange);
                coinDepthSet.add(coinDepth);
            }

            try {
                Thread.sleep(DEPTH_REQUEST_LIMIT);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepthSet;
    }

    private Mono<GateIOCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "_USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/spot/order_book")
                    .queryParam("currency_pair", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
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
            .bodyToMono(GateIOCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
