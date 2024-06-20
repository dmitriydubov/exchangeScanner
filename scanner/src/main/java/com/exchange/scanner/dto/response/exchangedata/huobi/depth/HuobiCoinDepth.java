package com.exchange.scanner.dto.response.exchangedata.huobi.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiCoinDepth {

    @JsonIgnore
    private String ch;

    @JsonIgnore
    private String status;

    @JsonIgnore
    private Long ts;

    @JsonIgnore
    private String coinName;

    private Tick tick;
}
