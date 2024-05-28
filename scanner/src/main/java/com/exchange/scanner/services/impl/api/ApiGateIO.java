package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.gateio.GateIOSymbolData;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.impl.api.utils.CoinFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiGateIO implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private final static String BASE_ENDPOINT = "https://api.gateio.ws/api/v4";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/spot/currencies";

        ResponseEntity<GateIOSymbolData[]> responseEntity = restTemplate.getForEntity(url, GateIOSymbolData[].class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Gate.io, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Gate.io, код: " + statusCode);
        }

        return Arrays.stream(responseEntity.getBody())
                .filter(symbol -> !symbol.getDeListed() &&
                        !symbol.getTradeDisabled() &&
                        !symbol.getWithdrawDisabled() &&
                        !symbol.getDepositDisabled()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getCurrency()))
                .collect(Collectors.toSet());
    }
}
