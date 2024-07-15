package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.bitget.chains.BitgetChainResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.depth.BitgetCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitget.coins.BitgetCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.tickervolume.BitgetTickerVolume;
import com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee.BitgetTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee.Data;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.Bitget.BitgetDepthBuilder;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiBitget implements ApiExchange {

    private static final String NAME = "Bitget";

    public final static String BASE_ENDPOINT = "https://api.bitget.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 500;

    private static final int DEPTH_REQUEST_LIMIT = 15;

    private final WebClient webClient;

    public ApiBitget() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_ENDPOINT, TIMEOUT);
    }

    @Override
    public Set<Coin> getAllCoins(Exchange exchange) {
        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Set<Coin> coins = new HashSet<>();

        BitgetCurrencyResponse response = getCurrencies().block();

        if (response == null || response.getData() == null) return coins;

        coins = response.getData().stream()
                .filter(symbol -> symbol.getQuoteCoin().equals("USDT") && symbol.getStatus().equals("online"))
                .map(symbol -> {
                    LinkDTO links = new LinkDTO();
                    links.setDepositLink(exchange.getDepositLink());
                    links.setWithdrawLink(exchange.getWithdrawLink());
                    links.setTradeLink(exchange.getTradeLink() + symbol.getBaseCoin().toUpperCase() + "USDT");
                    return ObjectUtils.getCoin(symbol.getBaseCoin(), NAME, links);
                })
                .collect(Collectors.toSet());

        return coins;
    }

    private Mono<BitgetCurrencyResponse> getCurrencies() {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/public/symbols")
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
            .bodyToMono(BitgetCurrencyResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();
        coins.forEach(coin -> {
            List<Chain> response = getChain(coin).block();
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

    private Mono<List<Chain>> getChain(Coin coin) {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/public/coins")
                    .queryParam("coin", coin.getName())
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
            .bodyToMono(BitgetChainResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            })
            .map(data -> data.getData().getFirst().getChains().stream()
                    .map(chainsDTO -> {
                        String chainName = chainsDTO.getChain();
                        if (chainName.equalsIgnoreCase("Polygon")) {
                            chainName = "Matic";
                        }
                        Chain chain = new Chain();
                        chain.setName(chainName.toUpperCase());
                        chain.setCommission(new BigDecimal(chainsDTO.getWithdrawFee()));
                        return chain;
                    })
                    .toList()
            );
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
            Data response = getFee(coin).block();

            if (response != null && response.getTakerFeeRate() != null) {
                TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                        exchangeName,
                        coin,
                        response.getTakerFeeRate()
                );
                tradingFeeSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return tradingFeeSet;
    }

    private Mono <Data> getFee(Coin coin) {
        String symbol = coin.getName() + "USDT";
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/public/symbols")
                    .queryParam("symbol", symbol)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitgetTradingFeeResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            })
            .map(response -> response.getData().getFirst());
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            BitgetTickerVolume response = getCoinTickerVolume(coin).block();

            if (response != null && response.getData() != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getData().getFirst().getQuoteVolume()
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

    private Mono<BitgetTickerVolume> getCoinTickerVolume(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/market/tickers")
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
            .bodyToMono(BitgetTickerVolume.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        try {
            Thread.sleep(REQUEST_DELAY_DURATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            BitgetCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = BitgetDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
                coinDepthSet.add(coinDepth);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        });

        return coinDepthSet;
    }

    private Mono<BitgetCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "USDT";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/spot/market/merge-depth")
                    .queryParam("symbol", symbol)
                    .queryParam("limit", DEPTH_REQUEST_LIMIT)
                    .build()
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения информации от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(BitgetCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
