package com.exchange.scanner.dto.response.exchangedata.coinex.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinExTicker {

    private List<CoinExTickerData> data;
}
