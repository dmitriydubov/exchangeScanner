package com.exchange.scanner.dto.response.exchangedata.bitmart.chains;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartChainsResponse {

    private String code;

    private String message;

    private BitmartChainsData data;
}
