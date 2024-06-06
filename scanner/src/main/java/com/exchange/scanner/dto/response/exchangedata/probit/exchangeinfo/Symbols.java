package com.exchange.scanner.dto.response.exchangedata.probit.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    private String id;

    @JsonProperty("deposit_suspended")
    private Boolean depositSuspended;

    @JsonProperty("withdrawal_suspended")
    private Boolean withdrawalSuspended;

    @JsonProperty("show_in_ui")
    private Boolean showInUI;
}
