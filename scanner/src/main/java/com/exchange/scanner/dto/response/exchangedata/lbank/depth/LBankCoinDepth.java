package com.exchange.scanner.dto.response.exchangedata.lbank.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankCoinDepth {

    @JsonIgnore
    private String result;

    @JsonIgnore
    private String msg;

    @JsonIgnore
    private String coinName;

    private Data data;

    @JsonProperty("error_code")
    @JsonIgnore
    private Integer errorCode;

    @JsonIgnore
    private Long ts;
}
