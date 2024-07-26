package com.exchange.scanner.dto.response.exchangedata.binance.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinanceNetwork {

    private String coin;

    private String network;

    private Integer minConfirm;

    private Boolean depositEnable;

    private Boolean withdrawEnable;

    private String withdrawFee;
}
