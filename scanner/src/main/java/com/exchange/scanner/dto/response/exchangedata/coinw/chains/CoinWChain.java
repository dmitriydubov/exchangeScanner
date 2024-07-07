package com.exchange.scanner.dto.response.exchangedata.coinw.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWChain {

    private String chain;

    private String txFee;

    private String recharge;

    private String withDraw;
}
