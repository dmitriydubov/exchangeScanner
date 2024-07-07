package com.exchange.scanner.dto.response.exchangedata.lbank.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankChainsData {

    @JsonProperty("chain")
    private String chain;

    @JsonProperty("fee")
    private String fee;
}
