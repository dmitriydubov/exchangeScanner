package com.exchange.scanner.dto.response.exchangedata.kucoin.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KucoinCurrencyResponse {

    private List<KucoinCurrencyData> data;
}
