package com.exchange.scanner.dto.response.exchangedata.huobi.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiTicker {

    private List<HuobiTickerData> data;
}
