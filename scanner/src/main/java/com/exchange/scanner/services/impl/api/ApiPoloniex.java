package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.poloniex.PoloniexSymbolData;
import com.exchange.scanner.dto.response.exchangedata.poloniex.Symbols;
import com.exchange.scanner.model.Coin;
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
public class ApiPoloniex implements ApiExchange {

    @Autowired
    private RestTemplate restTemplate;

    public final static String BASE_ENDPOINT = "https://api.poloniex.com/";

    @Override
    public Set<Coin> getAllCoins() {

        String url = BASE_ENDPOINT + "currencies";

        ResponseEntity<PoloniexSymbolData[]> responseEntity = restTemplate.getForEntity(url, PoloniexSymbolData[].class);
        HttpStatusCode statusCode = responseEntity.getStatusCode();

        if (statusCode != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Ошибка получения данных от Poloniex, код: {}", statusCode);
            throw new RuntimeException("Ошибка получения данных от Poloniex, код: " + statusCode);
        }

        return Arrays.stream(responseEntity.getBody()).filter(data -> {
            Symbols symbolSettings = data.getCurrencies().values().stream().reduce((symbols, symbols2) -> symbols).orElseThrow(() -> new RuntimeException("Ошибка получения данных с Poloniex"));
            return !symbolSettings.getDeListed() &&
                    symbolSettings.getTradingState().equals("NORMAL") &&
                    symbolSettings.getWalletDepositState().equals("ENABLED") &&
                    symbolSettings.getWalletWithdrawalState().equals("ENABLED");
        }).map(data -> {
            String coinName = data.getCurrencies().keySet().stream().reduce((key1, key2) -> key1).get();
            Coin coin = new Coin();
            coin.setName(coinName);
            coin.setSymbol(coinName);
            return coin;
        }).collect(Collectors.toSet());
    }
}
