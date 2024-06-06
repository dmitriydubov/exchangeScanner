package com.exchange.scanner.dto.response.exchangedata.mexc.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MexcSymbolData {

    private List<Symbols> symbols;
}
