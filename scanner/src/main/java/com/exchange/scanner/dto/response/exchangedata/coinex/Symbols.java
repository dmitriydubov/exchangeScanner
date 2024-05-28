package com.exchange.scanner.dto.response.exchangedata.coinex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    @JsonProperty("base_ccy")
    private String baseCcy;
}
