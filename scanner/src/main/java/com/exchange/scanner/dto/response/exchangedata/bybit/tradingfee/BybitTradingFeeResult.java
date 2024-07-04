package com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitTradingFeeResult {

    private List<BybitTradingFeeList> list;
}
