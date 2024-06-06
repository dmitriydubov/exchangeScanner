package com.exchange.scanner.dto.response.exchangedata.kucoin.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KucoinSymbolData {

    private List<Symbols> data;
}
