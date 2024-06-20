package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.model.Exchange;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

public interface AppService {

    Set<Exchange> getExchanges();

    ArbitrageEvent getArbitrageOpportunities(UserDetails userDetails);

    SimpleResponse refreshCoins();

    void getOrderBooks();
}
