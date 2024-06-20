package com.exchange.scanner.dto.response.exchangedata.kucoin.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KucoinCoinDepth {

    @JsonIgnore
    private String code;

    @JsonIgnore
    private String coinName;

    private Data data;
}
