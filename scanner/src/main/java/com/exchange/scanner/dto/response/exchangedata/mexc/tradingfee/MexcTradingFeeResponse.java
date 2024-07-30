package com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class MexcTradingFeeResponse {

    private List<MexcTradingFeeSymbol> symbols;
}
