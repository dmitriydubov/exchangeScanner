package com.exchange.scanner.services.impl.api.coinmarketcap;

import com.exchange.scanner.dto.response.CoinInfoDTO;
import com.exchange.scanner.dto.response.exchangedata.coinmarketcap.CoinMarketCapCurrencyResponse;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.CoinMarketCapService;
import com.exchange.scanner.services.utils.AppUtils.LogsUtils;
import com.exchange.scanner.services.utils.AppUtils.WebClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class CoinMarketCapServiceImpl implements CoinMarketCapService {

    @Value("${coinmarketcap.apikey}")
    private String apiKey;

    @Autowired
    private ObjectMapper objectMapper;

    private final WebClient webClient;

    private static final int TIMEOUT = 10000;

    private static final String BASE_URL = "https://pro-api.coinmarketcap.com";

    private static final String BASE_COIN_TRADE_URL = "https://coinmarketcap.com/currencies";

    public CoinMarketCapServiceImpl() {
        this.webClient = WebClientBuilder.buildWebClient(BASE_URL, TIMEOUT);
    }

    @Override
    public Set<CoinInfoDTO> getCoinMarketCapCoinsInfo(Set<Coin> coins, String exchange) {
        Set<CoinInfoDTO> coinInfoDTOSet = new HashSet<>();

        String response = getCoinInfo(coins).block();

        if (response == null || response.isEmpty()) return coinInfoDTOSet;

        try {
            CoinMarketCapCurrencyResponse dataResponse = objectMapper.readValue(response, CoinMarketCapCurrencyResponse.class);
            coins.forEach(coin -> {
                dataResponse.getData().forEach((key, value) ->{
                    if (coin.getName().equals(key)) {
                        CoinInfoDTO coinInfoDTO = new CoinInfoDTO();
                        coinInfoDTO.setExchange(exchange);
                        coinInfoDTO.setCoin(coin);
                        coinInfoDTO.setCoinMarketCapLink(BASE_COIN_TRADE_URL);
                        coinInfoDTO.setSlug(value.getFirst().getSlug());
                        coinInfoDTO.setLogoLink(value.getFirst().getLogo());
                        coinInfoDTOSet.add(coinInfoDTO);
                    }
                });
            });
        } catch (JsonProcessingException ex) {
            log.error("Ошибка десериализации Json от CoinMarketCap. Причина: {}", ex.getLocalizedMessage());
        }

        return coinInfoDTOSet;
    }

    private Mono<String> getCoinInfo(Set<Coin> coins) {
        String symbolParams = generateSymbolsParameters(coins);

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/cryptocurrency/info")
                        .queryParam("symbol", symbolParams)
                        .queryParam("aux", "logo")
                        .build()
                )
                .header("X-CMC_PRO_API_KEY", apiKey)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Ошибка получения информации о монете от CoinMarketCap. Причина: {}", errorBody);
                            return Mono.empty();
                        })
                )
                .bodyToMono(String.class)
                .onErrorResume(error -> {
                    LogsUtils.createErrorResumeLogs(error, "CoinMarketCap");
                    return Mono.empty();
                });
    }

    private static String generateSymbolsParameters(Set<Coin> coins) {
        if (coins.isEmpty()) return "";
        String parameters;
        StringBuilder sb = new StringBuilder();
        coins.forEach(coin -> sb.append(coin.getName()).append(","));
        sb.deleteCharAt(sb.length() - 1);
        parameters = sb.toString();

        return parameters;
    }
}
