package com.exchange.scanner.dto.response.exchangedata.coinex.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinExCurrencyResponse {

    private List<CoinExCurrencyData> data;
}
