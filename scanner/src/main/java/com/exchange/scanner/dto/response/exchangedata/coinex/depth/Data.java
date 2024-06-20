package com.exchange.scanner.dto.response.exchangedata.coinex.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Data {

    private Depth depth;

    @JsonProperty("is_full")
    @JsonIgnore
    private Boolean isFull;

    private String market;
}
