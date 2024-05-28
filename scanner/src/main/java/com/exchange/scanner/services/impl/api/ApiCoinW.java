package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinw.CoinWSymbolData;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.impl.api.utils.CoinFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiCoinW implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://www.coinw.com";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/appApi.html?action=currencys";

        ResponseEntity<CoinWSymbolData> responseEntity = restTemplate.getForEntity(url, CoinWSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от CoinW, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от CoinW, код: " + statusCode);
        }

        return responseEntity.getBody().getData().getSymbols().values().stream()
                .filter(symbol -> !symbol.getRecharge().equals("0") && !symbol.getWithDraw().equals("0"))
                .map(symbol -> CoinFactory.getCoin(symbol.getShortName()))
                .collect(Collectors.toSet());
    }
}
