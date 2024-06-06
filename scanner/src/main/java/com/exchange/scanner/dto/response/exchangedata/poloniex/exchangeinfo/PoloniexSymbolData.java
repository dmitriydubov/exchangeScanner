package com.exchange.scanner.dto.response.exchangedata.poloniex.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PoloniexSymbolData {

    private Map<String, Symbols> currencies = new HashMap<>();

    @JsonAnySetter
    public void addCurrency(String key, Symbols value) {
        currencies.put(key, value);
    }

}
