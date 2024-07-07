package com.exchange.scanner.dto.response.exchangedata.probit.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProbitTradingFeeResponse {

    private List<FeeData> data;
}
