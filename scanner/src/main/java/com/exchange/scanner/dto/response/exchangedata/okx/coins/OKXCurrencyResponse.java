package com.exchange.scanner.dto.response.exchangedata.okx.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXCurrencyResponse {

    private List<OKXSymbol> data;
}
