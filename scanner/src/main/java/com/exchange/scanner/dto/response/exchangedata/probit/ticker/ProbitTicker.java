package com.exchange.scanner.dto.response.exchangedata.probit.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProbitTicker {

    @JsonProperty("data")
    private List<ProbitTickerData> data;
}
