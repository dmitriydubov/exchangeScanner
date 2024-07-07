package com.exchange.scanner.dto.response.exchangedata.lbank.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankChainsResponse {

    @JsonProperty("data")
    private List<LBankChainsData> data;
}
