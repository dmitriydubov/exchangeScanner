package com.exchange.scanner.dto.response.exchangedata.poloniex.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PoloniexTradingFeeResponse {

    private List<PoloniexFeeRate> specialFeeRates;
}
