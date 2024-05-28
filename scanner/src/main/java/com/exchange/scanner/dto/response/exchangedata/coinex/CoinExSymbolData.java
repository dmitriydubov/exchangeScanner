package com.exchange.scanner.dto.response.exchangedata.coinex;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinExSymbolData {

    private List<Symbols> data;
}
