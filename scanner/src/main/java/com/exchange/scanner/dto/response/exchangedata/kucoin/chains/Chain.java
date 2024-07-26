package com.exchange.scanner.dto.response.exchangedata.kucoin.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Chain {

    private String chainName;

    private String withdrawalMinFee;

    private Boolean isWithdrawEnabled;

    private Boolean isDepositEnabled;

    private Integer confirms;
}
