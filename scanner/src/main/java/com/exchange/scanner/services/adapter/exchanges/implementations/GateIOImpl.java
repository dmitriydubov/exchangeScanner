package com.exchange.scanner.services.adapter.exchanges.implementations;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.exchangedto.GateIODTO;
import com.exchange.scanner.dto.CoinData;
import com.exchange.scanner.services.adapter.exchanges.GateIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class GateIOImpl implements GateIO {

    private final RestTemplate restTemplate;
    private final Logger logger = LoggerFactory.getLogger(GateIOImpl.class);
    private final static String EXCHANGE_NAME = "gateio";
    private final static String HOST = "https://api.gateio.ws";

    @Autowired
    public GateIOImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @Override
    public List<CoinData> getTickerData(MarketSettings marketSettings) {
        List<CoinData> coinDataList = new ArrayList<>();
        String prefix = "/api/v4/spot/tickers";
        marketSettings.getCoins().forEach(coin -> {
            String queryParam = "?currency_pair=" + coin + "_USDT";
            String url = HOST + prefix + queryParam;
            CoinData coinData = new CoinData();
            logger.info("запрос {}", url);
            GateIODTO[] gateIODTOs = restTemplate.getForObject(url, GateIODTO[].class);
            //Написать кастомный exception handler (NullPointer)
            //^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            if (gateIODTOs != null) {
                Arrays.stream(gateIODTOs).forEach(dto -> {
                    coinData.setSymbol(dto.getCurrencyPair());
                    coinData.setPrice(dto.getLast());
                    coinData.setExchange(EXCHANGE_NAME);
                    coinDataList.add(coinData);
                });
            } else {
                logger.error("Ошибка при обработке ответа от Gateio");
            }
            logger.info("успешный ответ Gateio {}", url);
        });

        return coinDataList;
    }
}
