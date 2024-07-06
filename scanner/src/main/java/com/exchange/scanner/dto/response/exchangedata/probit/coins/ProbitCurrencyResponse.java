package com.exchange.scanner.dto.response.exchangedata.probit.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class ProbitCurrencyResponse {

    private List<ProbitCurrencyData> data;
}
