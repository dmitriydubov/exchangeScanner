package com.exchange.scanner.dto.response.exchangedata.bybit.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitCurrencyResult {

    private List<BybitCurrencies> list;
}
