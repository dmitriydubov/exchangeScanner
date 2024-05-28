package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.okx.OKXSymbolData;
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
public class ApiOKX implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://www.okx.com";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/api/v5/public/instruments?instType=SPOT";

        ResponseEntity<OKXSymbolData> responseEntity = restTemplate.getForEntity(url, OKXSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от OKX, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от OKX, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getState().equals("live"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCcy()))
                .collect(Collectors.toSet());
    }
}
