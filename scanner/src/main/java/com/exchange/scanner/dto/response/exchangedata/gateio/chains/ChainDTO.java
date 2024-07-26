package com.exchange.scanner.dto.response.exchangedata.gateio.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChainDTO {

    private String currency;

    private String chain;

    @JsonProperty("withdraw_disabled")
    private Boolean withdrawDisabled;

    @JsonProperty("deposit_disabled")
    private Boolean depositDisabled;
}
