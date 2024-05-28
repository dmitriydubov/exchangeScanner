package com.exchange.scanner.dto.response.exchangedata.bitmart;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    private String id;

    @JsonProperty("withdraw_enabled")
    private Boolean withdrawEnabled;

    @JsonProperty("deposit_enabled")
    private Boolean depositEnabled;
}
