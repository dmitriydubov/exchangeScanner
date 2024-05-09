package com.exchange.scanner.service.adapter.exchanges.implementations;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.CoinData;
import com.exchange.scanner.dto.exchangedto.BinanceDTO;
import com.exchange.scanner.service.adapter.exchanges.Binance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class BinanceImpl implements Binance {
    private final RestTemplate restTemplate;
    private final Logger logger = LoggerFactory.getLogger(BinanceImpl.class);
    private final static String HOST = "https://testnet.binancefuture.com";
    private final static String EXCHANGE_NAME = "binance";

    @Autowired
    public BinanceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @Override
    public List<CoinData> getTickerData(MarketSettings marketSettings) throws HttpClientErrorException {
        List<CoinData> coinDataList = new ArrayList<>();
        String prefix = "/fapi/v1/ticker/24hr";
        marketSettings.getCoins().forEach(coin -> {
            String queryParam = "?symbol=" + coin + "USDT";
            String url = HOST + prefix + queryParam;
            logger.info("запрос к Binance {}", url);
            BinanceDTO binanceDTO = restTemplate.getForObject(url, BinanceDTO.class);
            if (binanceDTO != null) {
                CoinData coinData = new CoinData();
                coinData.setSymbol(binanceDTO.getSymbol());
                coinData.setPrice(binanceDTO.getLastPrice());
                coinData.setExchange(EXCHANGE_NAME);
                coinDataList.add(coinData);
            } else {
                logger.error("Ошибка при обработке ответа от Binance");
            }
            logger.info("успешный ответ от Binance {}", url);
        });

        return coinDataList;
    }
}
