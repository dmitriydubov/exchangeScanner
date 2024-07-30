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
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.XT.XTCoinDepthBuilder;
import com.exchange.scanner.services.utils.XT.XTSignatureBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiXT implements ApiExchange {

    @Value("${exchanges.apiKeys.XT.key}")
    private String key;

    @Value("${exchanges.apiKeys.XT.secret}")
    private String secret;

    private static final String NAME = "XT";

    public final static String BASE_ENDPOINT = "https://sapi.xt.com";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 20;

    private static final int DEPTH_REQUEST_LIMIT = 10;

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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            XTCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getResult() != null) {
                CoinDepth coinDepth = XTCoinDepthBuilder.getCoinDepth(coin, response.getResult(), exchange);
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

    private Mono<XTCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName().toLowerCase() + "_usdt";

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/v4/public/depth")
                    .queryParam("symbol", symbol)
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
            .bodyToMono(XTCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName().toLowerCase())
                .append("_usdt")
                .append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
