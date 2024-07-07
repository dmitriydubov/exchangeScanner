package com.exchange.scanner.dto.response.exchangedata.bitmart.tickervolume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartVolumeTickerData {

    @JsonProperty("qv_24h")
    private String qv24h;
}
