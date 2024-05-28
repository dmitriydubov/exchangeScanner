package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.probit.ProbitSymbolData;
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
public class ApiProbit implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://api.probit.com/api/exchange/v1";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/currency";

        ResponseEntity<ProbitSymbolData> responseEntity = restTemplate.getForEntity(url, ProbitSymbolData.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Probit, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Probit, код: " + statusCode);
        }

        return responseEntity.getBody().getData().stream()
                .filter(symbol -> symbol.getShowInUI() &&
                        !symbol.getDepositSuspended() &&
                        !symbol.getWithdrawalSuspended()
                )
                .map(symbol -> CoinFactory.getCoin(symbol.getId()))
                .collect(Collectors.toSet());
    }
}
