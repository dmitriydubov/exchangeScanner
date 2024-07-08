package com.exchange.scanner.dto.response.exchangedata.binance.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BinanceChainResponse {

    private String coin;

    private List<BinanceNetwork> networkList;

    private Boolean trading;
}
