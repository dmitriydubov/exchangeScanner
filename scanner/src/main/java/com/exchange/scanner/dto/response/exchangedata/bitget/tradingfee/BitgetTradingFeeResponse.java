package com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetTradingFeeResponse {

    private List<Data> data;
}
