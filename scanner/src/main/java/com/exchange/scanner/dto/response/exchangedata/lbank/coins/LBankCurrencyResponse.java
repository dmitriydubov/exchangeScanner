package com.exchange.scanner.dto.response.exchangedata.lbank.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankCurrencyResponse {

    private List<LBankCurrencyData> data;
}
