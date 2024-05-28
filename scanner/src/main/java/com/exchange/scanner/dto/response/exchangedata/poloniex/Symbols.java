package com.exchange.scanner.dto.response.exchangedata.poloniex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    @JsonProperty("delisted")
    private Boolean deListed;

    private String tradingState;

    private String walletDepositState;

    private String walletWithdrawalState;
}
