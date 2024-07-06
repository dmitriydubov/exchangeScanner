package com.exchange.scanner.dto.response.exchangedata.huobi.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiCurrencyResponse {

    private List<HuobiCurrencyData> data;
}
