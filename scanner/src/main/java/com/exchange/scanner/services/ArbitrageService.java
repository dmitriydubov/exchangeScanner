package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.event.UserTradeEvent;
import com.exchange.scanner.dto.response.event.ArbitrageOpportunity;

import java.util.Set;

public interface ArbitrageService {

    Set<ArbitrageOpportunity> getArbitrageOpportunities(UserTradeEvent userTradeEvent);
}
