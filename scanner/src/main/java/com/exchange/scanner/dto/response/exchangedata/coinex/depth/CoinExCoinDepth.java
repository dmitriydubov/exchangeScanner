package com.exchange.scanner.dto.response.exchangedata.coinex.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinExCoinDepth {

    @JsonIgnore
    private Integer code;

    private Data data;

    @JsonIgnore
    private String message;
}
