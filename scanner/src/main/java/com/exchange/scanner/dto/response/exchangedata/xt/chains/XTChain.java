package com.exchange.scanner.dto.response.exchangedata.xt.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class XTChain {

    private String chain;

    private String withdrawFeeAmount;

    private String depositFeeRate;

    private Boolean depositEnabled;

    private Boolean withdrawEnabled;
}
