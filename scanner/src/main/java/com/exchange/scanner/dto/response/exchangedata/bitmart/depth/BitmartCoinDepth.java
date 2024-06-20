package com.exchange.scanner.dto.response.exchangedata.bitmart.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartCoinDepth {

    @JsonIgnore
    private Integer code;

    @JsonIgnore
    private String trace;

    @JsonIgnore
    private String message;

    private Data data;
}
