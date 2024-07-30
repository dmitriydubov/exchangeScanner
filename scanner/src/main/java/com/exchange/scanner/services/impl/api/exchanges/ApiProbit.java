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
import com.exchange.scanner.services.utils.AppUtils.*;
import com.exchange.scanner.services.utils.Probit.ProbitCoinDepthBuilder;
import lombok.extern.slf4j.Slf4j;
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
public class ApiProbit implements ApiExchange {

    private static final String NAME = "Probit";

    public final static String BASE_ENDPOINT = "https://api.probit.com/api/exchange/v1";

    private static final int TIMEOUT = 10000;

    private static final int REQUEST_DELAY_DURATION = 200;

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
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }


    @Override
    public Set<CoinDepth> getOrderBook(Set<Coin> coins, String exchange) {
        Set<CoinDepth> coinDepthSet = new HashSet<>();

        coins.forEach(coin -> {
            ProbitCoinDepth response = getCoinDepth(coin).block();

            if (response != null && response.getData() != null) {
                CoinDepth coinDepth = ProbitCoinDepthBuilder.getCoinDepth(coin, response.getData(), exchange);
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

    private Mono<ProbitCoinDepth> getCoinDepth(Coin coin) {
        String symbol = coin.getName() + "-USDT";
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/order_book")
                    .queryParam("market_id", symbol)
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
            .bodyToMono(ProbitCoinDepth.class)
            .onErrorResume(error -> {
                LogsUtils.createErrorResumeLogs(error, NAME);
                return Mono.empty();
            });
    }

    private static String generateParameters(List<Coin> coins) {
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName()).append("-USDT").append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
