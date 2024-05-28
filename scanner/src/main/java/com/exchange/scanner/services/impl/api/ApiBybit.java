package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bybit.BybitSymbolData;
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
public class ApiBybit implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://api-testnet.bybit.com";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/v3/public/symbols";

        ResponseEntity<BybitSymbolData> responseEntity = restTemplate.getForEntity(url, BybitSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Bybit, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Bybit, код: " + statusCode);
        }

        return responseEntity.getBody()
                .getResult().getList()
                .stream().filter(symbol -> symbol.getShowStatus().equals("1"))
                .map(symbol -> CoinFactory.getCoin(symbol.getBaseCoin()))
                .collect(Collectors.toSet());
    }
}
