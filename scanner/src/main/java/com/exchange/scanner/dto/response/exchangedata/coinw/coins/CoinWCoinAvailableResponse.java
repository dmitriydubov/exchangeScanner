package com.exchange.scanner.dto.response.exchangedata.coinw.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class CoinWCoinAvailableResponse {

    private List<CoinWCurrency> data;
}
