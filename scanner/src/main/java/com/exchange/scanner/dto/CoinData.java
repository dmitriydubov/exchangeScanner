package com.exchange.scanner.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinData {
    private String symbol;
    private String price;
    private String exchange;

    @Override
    public String toString() {
        return symbol;
    }
}
