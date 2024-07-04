package com.exchange.scanner.dto.response.exchangedata.coinw.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class CoinWSymbolData {

    private List<Symbols> data;
}
