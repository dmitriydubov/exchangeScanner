package com.exchange.scanner.services.impl;

import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.services.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    private final ExchangeRepository exchangeRepository;

    @Override
    public Set<Exchange> getExchanges() {
        return new HashSet<>(exchangeRepository.findAll());
    }
}
