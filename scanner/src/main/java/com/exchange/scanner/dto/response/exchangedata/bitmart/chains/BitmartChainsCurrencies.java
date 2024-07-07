package com.exchange.scanner.dto.response.exchangedata.bitmart.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartChainsCurrencies {

    private String currency;

    private String network;

    @JsonProperty("withdraw_minfee")
    private String withdrawMinFee;
}
