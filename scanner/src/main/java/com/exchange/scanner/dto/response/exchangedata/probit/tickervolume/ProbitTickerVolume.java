package com.exchange.scanner.dto.response.exchangedata.probit.tickervolume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProbitTickerVolume {

    @JsonProperty("data")
    private List<ProbitTickerData> data;
}
