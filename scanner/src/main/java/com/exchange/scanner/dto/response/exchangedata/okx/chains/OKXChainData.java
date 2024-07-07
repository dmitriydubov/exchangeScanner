package com.exchange.scanner.dto.response.exchangedata.okx.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OKXChainData {

    private String ccy;

    private String chain;

    private String maxFee;
}
