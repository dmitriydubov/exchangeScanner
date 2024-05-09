package com.exchange.scanner.service;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.Response;

public interface ExchangeService {
    Response getInfo(MarketSettings marketSettings);
}
