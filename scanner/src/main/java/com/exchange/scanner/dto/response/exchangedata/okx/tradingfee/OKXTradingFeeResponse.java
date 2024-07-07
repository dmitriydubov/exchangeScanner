package com.exchange.scanner.dto.response.exchangedata.okx.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXTradingFeeResponse {

    private List<OKXTradingFeeData> data;
}
