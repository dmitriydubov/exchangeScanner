package com.exchange.scanner.dto.response.exchangedata.bitget.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Chain {

    private String chain;

    private String withdrawable;

    private String rechargeable;

    private String withdrawFee;
}
