package com.exchange.scanner.dto.response.exchangedata.xt.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTCurrencyResult {

    private List<XTCurrencies> symbols;
}
