package com.exchange.scanner.dto.response.exchangedata.bitget.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetCurrencyResponse {

    private List<BitgetCurrencies> data;
}
