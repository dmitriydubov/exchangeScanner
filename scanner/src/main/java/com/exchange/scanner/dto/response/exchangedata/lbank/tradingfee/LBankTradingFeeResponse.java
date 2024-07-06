package com.exchange.scanner.dto.response.exchangedata.lbank.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankTradingFeeResponse {

    private List<LBankFeeData> data;
}
