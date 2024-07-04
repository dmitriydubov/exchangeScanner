package com.exchange.scanner.dto.response.exchangedata.gateio.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChainDTO {

    private String chain;

    @JsonProperty("is_disabled")
    private Integer isDisabled;

    @JsonProperty("is_deposit_disabled")
    private Integer isDepositDisabled;

    @JsonProperty("is_withdraw_disabled")
    private Integer isWithdrawDisabled;
}
