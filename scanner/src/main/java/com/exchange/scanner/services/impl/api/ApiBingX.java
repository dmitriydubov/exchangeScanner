package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.bingx.BingXSymbolData;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.services.impl.api.utils.CoinFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiBingX implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    public final static String BASE_ENDPOINT = "https://open-api.bingx.com";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "/openApi/spot/v1/common/symbols";

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от BingX, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от BingX, код: " + statusCode);
        }

        try {
            BingXSymbolData data = objectMapper.readValue(responseEntity.getBody(), BingXSymbolData.class);
            return data.getData().getSymbols().stream()
                    .filter(symbol -> symbol.getStatus() == 1)
                    .map(symbol -> {
                        String coinName = CoinFactory.refactorToStandardCoinName(symbol.getSymbol(), "-");
                        return CoinFactory.getCoin(coinName);
                    })
                    .collect(Collectors.toSet());
        } catch (IOException ex) {
            log.error("Ошибка десериализации ответа от BingX", ex);
            throw new RuntimeException("Ошибка десериализации ответа от BingX", ex);
        }
    }
}
