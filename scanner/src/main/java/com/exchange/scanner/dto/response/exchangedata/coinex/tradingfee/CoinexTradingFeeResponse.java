package com.exchange.scanner.dto.response.exchangedata.coinex.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinexTradingFeeResponse {

    private List<CoinexFeeData> data;
}
