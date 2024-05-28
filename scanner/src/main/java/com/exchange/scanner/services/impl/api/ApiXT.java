package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.xt.XTSymbolData;
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
public class ApiXT implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://sapi.xt.com";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/v4/public/symbol";

        ResponseEntity<XTSymbolData> responseEntity = restTemplate.getForEntity(url, XTSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от XT, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от XT, код: " + statusCode);
        }

        return responseEntity.getBody().getResult().getSymbols().stream()
                .filter(symbol -> symbol.getTradingEnabled() && symbol.getState().equals("ONLINE"))
                .map(symbol -> {
                    String coinName = symbol.getBaseCurrency().toUpperCase();
                    return CoinFactory.getCoin(coinName);
                })
                .collect(Collectors.toSet());
    }
}
