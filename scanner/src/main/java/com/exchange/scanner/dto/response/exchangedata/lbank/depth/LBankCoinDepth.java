package com.exchange.scanner.dto.response.exchangedata.lbank.depth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankCoinDepth {

    private LBankDepthData depth;

    private String pair;
}
