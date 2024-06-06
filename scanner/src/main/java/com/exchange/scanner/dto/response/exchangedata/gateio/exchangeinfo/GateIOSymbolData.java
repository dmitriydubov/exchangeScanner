package com.exchange.scanner.dto.response.exchangedata.gateio.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GateIOSymbolData {

    private String currency;

    @JsonProperty("delisted")
    private Boolean deListed;

    @JsonProperty("trade_disabled")
    private Boolean tradeDisabled;

    @JsonProperty("withdraw_disabled")
    private Boolean withdrawDisabled;

    @JsonProperty("deposit_disabled")
    private Boolean depositDisabled;
}
