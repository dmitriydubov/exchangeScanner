package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.exchangedata.ExchangeDataResponse;
import com.exchange.scanner.model.Exchange;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

public interface AppService {

    Set<Exchange> getExchanges();

    ExchangeDataResponse getExchangeData(UserDetails userDetails);

    SimpleResponse refreshCoins();

    void checkArbitrageOpportunities();
}
