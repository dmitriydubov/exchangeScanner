package com.exchange.scanner.dto.response.exchangedata.coinw.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinWTicker {

    private List<CoinWTickerData> data;
}
