package com.exchange.scanner.dto.response.exchangedata.bingx.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BingXCoinDepth {

    @JsonIgnore
    private Integer code;

    @JsonIgnore
    private Long timestamp;

    @JsonIgnore
    private String coinName;

    private Data data;
}
