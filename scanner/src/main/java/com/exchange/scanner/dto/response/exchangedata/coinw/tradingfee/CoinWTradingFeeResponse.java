package com.exchange.scanner.dto.response.exchangedata.coinw.tradingfee;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

@Getter
@Setter
public class CoinWTradingFeeResponse {

    private String code;

    private String msg;

    private Map<String, CoinWFeeData> data;
}
