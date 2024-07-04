package com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiVolumeTicker {

    @JsonProperty("tick")
    private Tick tick;
}
