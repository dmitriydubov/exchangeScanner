package com.exchange.scanner.dto.response.exchangedata.huobi;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiSymbolData {

    private List<Symbols> data;
}
