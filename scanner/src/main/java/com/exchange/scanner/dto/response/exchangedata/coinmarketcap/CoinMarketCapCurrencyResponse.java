package com.exchange.scanner.dto.response.exchangedata.coinmarketcap;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;


@Getter
@Setter
public class CoinMarketCapCurrencyResponse {

    @JsonProperty("data")
    private Map<String, List<CoinMarketCapCurrencyData>> data;
}
