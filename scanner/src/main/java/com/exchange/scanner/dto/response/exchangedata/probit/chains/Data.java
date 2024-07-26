package com.exchange.scanner.dto.response.exchangedata.probit.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private String id;

    private String platform;

    @JsonProperty("withdrawal_fee")
    private List<WithdrawalFee> withdrawalFee;

    @JsonProperty("deposit_suspended")
    private Boolean depositSuspended;

    @JsonProperty("withdrawal_suspended")
    private Boolean withdrawalSuspended;

    @JsonProperty("min_confirmation_count")
    private Integer minConfirmationCount;
}
