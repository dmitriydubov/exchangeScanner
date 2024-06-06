package com.exchange.scanner.dto.response.exchangedata.binance.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExchangeInfo {

    private List<Symbols> symbols;
}
