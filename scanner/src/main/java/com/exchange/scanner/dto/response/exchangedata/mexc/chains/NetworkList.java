package com.exchange.scanner.dto.response.exchangedata.mexc.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkList {

    private String netWork;

    private Boolean withdrawEnable;

    private Boolean depositEnable;

    private String withdrawFee;
}
