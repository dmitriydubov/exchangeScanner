package com.exchange.scanner.dto.response.exchangedata.lbank.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankChainsData {

    @JsonProperty("assetCode")
    private String assetCode;

    @JsonProperty("chain")
    private String chain;

    @JsonProperty("fee")
    private String fee;

    @JsonProperty("canWithDraw")
    private Boolean canWithdraw;
}
