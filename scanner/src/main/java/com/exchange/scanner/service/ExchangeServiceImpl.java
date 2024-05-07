package com.exchange.scanner.service;

import com.exchange.scanner.dto.Response;
import com.exchange.scanner.model.CoinData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    public Response getInfo() {
        List<CoinData> coinDataList = exchangeFacade.getDataFromExchanges(new ArrayList<>());
        return new Response(true, null);
    }
}
