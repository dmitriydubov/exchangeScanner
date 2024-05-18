package com.exchange.scanner.services;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.Response;

public interface ExchangeService {
    Response getInfo(MarketSettings marketSettings);
}
