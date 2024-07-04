package com.exchange.scanner.dto.response.exchangedata.probit.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private String id;

    @JsonProperty("withdrawal_fee")
    private List<WithdrawalFee> withdrawalFee;
}
