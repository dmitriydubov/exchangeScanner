package com.exchange.scanner.dto.response.exchangedata.gateio.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GateIOSymbolData {

    private String base;

    private String quote;

    @JsonProperty("trade_status")
    private String tradeStatus;
}
