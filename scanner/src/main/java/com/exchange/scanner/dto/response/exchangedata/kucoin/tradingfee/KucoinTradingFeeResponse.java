package com.exchange.scanner.dto.response.exchangedata.kucoin.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KucoinTradingFeeResponse {

    private List<Data> data;
}
