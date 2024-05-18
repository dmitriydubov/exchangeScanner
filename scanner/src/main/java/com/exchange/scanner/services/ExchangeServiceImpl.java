package com.exchange.scanner.services;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.Response;
import com.exchange.scanner.dto.CoinData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import java.util.List;

@Service
public class ExchangeServiceImpl implements ExchangeService {
    private final ExchangeFacade exchangeFacade;
    private final Logger logger = LoggerFactory.getLogger(ExchangeServiceImpl.class);

    @Autowired
    public ExchangeServiceImpl(ExchangeFacade exchangeFacade) {
        this.exchangeFacade = exchangeFacade;
    }

    @Override
    public Response getInfo(MarketSettings marketSettings) throws HttpClientErrorException {
        List<CoinData> coinDataList = exchangeFacade.getDataFromExchanges(marketSettings);;
        return new Response(true, "success", coinDataList);
    }
}
