package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.huobi.HuobiSymbolData;
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
public class ApiHuobi implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://api.huobi.pro";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/v2/settings/common/currencies";

        ResponseEntity<HuobiSymbolData> responseEntity = restTemplate.getForEntity(url, HuobiSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Huobi, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Huobi, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getDe() && symbol.getWed())
                .map(symbol -> CoinFactory.getCoin(symbol.getDn()))
                .collect(Collectors.toSet());
    }
}
