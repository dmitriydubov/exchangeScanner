package com.exchange.scanner.dto.response.exchangedata.bitget;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetSymbolData {

    private List<Symbols> data;
}
