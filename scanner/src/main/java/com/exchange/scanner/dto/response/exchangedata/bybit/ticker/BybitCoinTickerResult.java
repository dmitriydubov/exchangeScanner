package com.exchange.scanner.dto.response.exchangedata.bybit.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitCoinTickerResult {

    private List<BybitCoinTickerList> list;
}
