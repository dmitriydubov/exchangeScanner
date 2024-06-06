package com.exchange.scanner.dto.response.exchangedata.lbank.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankTicker {

    private List<LBankTickerData> data;
}
