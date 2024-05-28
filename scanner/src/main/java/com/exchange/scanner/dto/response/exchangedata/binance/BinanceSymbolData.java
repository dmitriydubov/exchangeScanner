package com.exchange.scanner.dto.response.exchangedata.binance;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BinanceSymbolData {

    private List<Symbols> symbols;
}
