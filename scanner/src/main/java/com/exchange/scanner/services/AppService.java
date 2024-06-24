package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.model.Exchange;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;

public interface AppService {

    Set<String> getExchanges();

    List<ArbitrageEvent> getArbitrageOpportunities(UserDetails userDetails);

    void refreshCoins();

    void getOrderBooks();
}
