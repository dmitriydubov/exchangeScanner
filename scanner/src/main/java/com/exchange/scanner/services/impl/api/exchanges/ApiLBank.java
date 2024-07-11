package com.exchange.scanner.services.impl.api.exchanges;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.lbank.chains.LBankChainsResponse;
import com.exchange.scanner.dto.response.exchangedata.lbank.depth.LBankCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.lbank.coins.LBankCurrencyResponse;
import com.exchange.scanner.dto.response.exchangedata.lbank.tickervolume.LBankVolumeTicker;
import com.exchange.scanner.dto.response.exchangedata.lbank.tradingfee.LBankTradingFeeResponse;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.AppUtils.ObjectUtils;
import com.exchange.scanner.services.utils.LBank.LBankCoinDepthBuilder;
import com.exchange.scanner.services.utils.LBank.LBankSignatureBuilder;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiLBank implements ApiExchange {

    @Value("${exchanges.apiKeys.LBank.key}")
    private String key;

    @Value("${exchanges.apiKeys.LBank.secret}")
    private String secret;

    private static final String NAME = "LBank";

    public final static String BASE_ENDPOINT = "https://www.lbkex.net";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 100;

    private static final int DEPTH_REQUEST_LIMIT = 20;

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
                    return ObjectUtils.getCoin(coinName, NAME, links);
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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName) {
        Set<ChainResponseDTO> chainsDTOSet = new HashSet<>();

        coins.forEach(coin -> {
            LBankChainsResponse response = getChains(coin).block();

            if (response != null && response.getData() != null) {
                Set<Chain> chains = new HashSet<>();
                response.getData().forEach(data -> {
                    if (data.getChain() != null) {
                        Chain chain = new Chain();
                        chain.setName(data.getChain().toUpperCase());
                        if (data.getFee() != null) {
                            chain.setCommission(new BigDecimal(data.getFee()));
                        } else {
                            chain.setCommission(new BigDecimal(BigInteger.ZERO));
                        }
                        chains.add(chain);
                    }
                });
                ChainResponseDTO responseDTO = ObjectUtils.getChainResponseDTO(exchangeName, coin, chains);
                chainsDTOSet.add(responseDTO);
            }

            try {
                Thread.sleep(REQUEST_DELAY_DURATION);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        return chainsDTOSet;
    }

    private Mono<LBankChainsResponse> getChains(Coin coin) {
        String requestPath = "/v2/withdrawConfigs.do";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path(requestPath)
                    .queryParam("assetCode", coin.getName().toLowerCase())
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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchangeName) {
        Set<TradingFeeResponseDTO> tradingFeeSet = new HashSet<>();

        coins.forEach(coin -> {
           LBankTradingFeeResponse response = getFee(coin).block();

           if (response != null && response.getData() != null) {
               TradingFeeResponseDTO responseDTO = ObjectUtils.getTradingFeeResponseDTO(
                       exchangeName,
                       coin,
                       response.getData().getFirst().getTakerCommission()
               );
               tradingFeeSet.add(responseDTO);
           }
           try {
               Thread.sleep(REQUEST_DELAY_DURATION);
           } catch (InterruptedException ex) {
               throw new RuntimeException(ex);
           }
        });

        return tradingFeeSet;
    }

    private Mono<LBankTradingFeeResponse> getFee(Coin coin) {
        String requestPath = "/v2/supplement/customer_trade_fee.do";
        String symbol = coin.getName().toLowerCase() + "_usdt";
        TreeMap<String, String> initialParams = new TreeMap<>();
        initialParams.put("category", symbol);
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
                        log.error("Ошибка получения торговой комиссии от " + NAME + ". Для торговой пары {}. Причина: {}", symbol, errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(LBankTradingFeeResponse.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchange) {
        Set<Volume24HResponseDTO> volume24HSet = new HashSet<>();

        coins.forEach(coin -> {
            LBankVolumeTicker response = getCoinTicker(coin).block();

            if (response != null && response.getData() != null) {
                Volume24HResponseDTO responseDTO = ObjectUtils.getVolume24HResponseDTO(
                        exchange,
                        coin,
                        response.getData().getFirst().getTicker().getTurnover()
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

    private Mono<LBankVolumeTicker> getCoinTicker(Coin coin) {
        String symbol = coin.getName().toLowerCase() + "_usdt";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/ticker/24hr.do")
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
            .bodyToMono(LBankVolumeTicker.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            LBankCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = LBankCoinDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
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

    private Mono<LBankCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName().toLowerCase() + "_usdt";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/v2/depth.do")
                    .queryParam("symbol", symbol)
                    .queryParam("size", DEPTH_REQUEST_LIMIT)
                    .build())
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Ошибка получения order book от " + NAME + ". Причина: {}", errorBody);
                        return Mono.empty();
                    })
            )
            .bodyToMono(LBankCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }
}
