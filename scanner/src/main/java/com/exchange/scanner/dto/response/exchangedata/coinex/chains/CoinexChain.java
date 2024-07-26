package com.exchange.scanner.dto.response.exchangedata.coinex.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinexChain {

    private String chain;

    @JsonProperty("withdrawal_fee")
    private String withdrawalFee;

    @JsonProperty("deposit_enabled")
    private Boolean depositEnabled;

    @JsonProperty("withdraw_enabled")
    private Boolean withdrawEnabled;

    @JsonProperty("irreversible_confirmations")
    private Integer irreversibleConfirmations;
}
