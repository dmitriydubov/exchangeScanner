package com.exchange.scanner.dto.response.exchangedata.bitget.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitgetCoinDepth {

    @JsonIgnore
    private String code;

    @JsonIgnore
    private String msg;

    @JsonIgnore
    private Long requestTime;

    @JsonIgnore
    private String coinName;

    private Data data;
}
