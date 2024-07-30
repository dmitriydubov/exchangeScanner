package com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiTradingFeeResponse {

    private Integer code;

    private List<HuobiFeeData> data;
}
