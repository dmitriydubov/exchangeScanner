package com.exchange.scanner.dto.response.exchangedata.okx.depth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Ask {

    @JsonProperty(index = 0)
    private String price;

    @JsonProperty(index = 1)
    private String volume;

    public Ask(String price, String volume) {
        this.price = price;
        this.volume = volume;
    }
}
