package com.exchange.scanner.dto.response.exchangedata.probit.chains;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WithdrawalFee {

    @JsonProperty("currency_id")
    private String currencyId;

    private String amount;
}
