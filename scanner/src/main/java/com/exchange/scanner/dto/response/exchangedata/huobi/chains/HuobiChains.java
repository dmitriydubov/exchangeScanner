package com.exchange.scanner.dto.response.exchangedata.huobi.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiChains {

    private String displayName;

    private String transactFeeWithdraw;

    private Integer numOfConfirmations;
}
