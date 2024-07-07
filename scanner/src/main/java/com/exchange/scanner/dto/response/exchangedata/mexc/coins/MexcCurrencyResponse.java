package com.exchange.scanner.dto.response.exchangedata.mexc.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MexcCurrencyResponse {

    private List<MexcCurrencySymbols> symbols;
}
