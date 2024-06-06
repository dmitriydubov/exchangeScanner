package com.exchange.scanner.dto.response.exchangedata.bitmart.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitmartTicker {

    @JsonProperty("data")
    private List<List<Object>> data;
}
