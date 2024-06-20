package com.exchange.scanner.dto.response.exchangedata.coinw.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWCoinDepth {

    @JsonIgnore
    private String coinName;

    private Data data;
}
