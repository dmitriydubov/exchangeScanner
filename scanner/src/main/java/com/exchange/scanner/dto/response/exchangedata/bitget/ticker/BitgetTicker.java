package com.exchange.scanner.dto.response.exchangedata.bitget.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetTicker {

    private List<BitgetTickerData> data;
}
