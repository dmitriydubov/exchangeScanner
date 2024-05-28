package com.exchange.scanner.dto.response.exchangedata.coinw;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Data  {

    private Map<String, Symbols> symbols = new HashMap<>();

    @JsonAnySetter
    public void setSymbols(String key, Symbols value) {
        symbols.put(key, value);
    }
}
