package com.exchange.scanner.dto.response.exchangedata.bingx.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BingXTicker {

    private List<BingXTickerData> data;
}
