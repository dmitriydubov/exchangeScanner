package com.exchange.scanner.dto.response.exchangedata.bitget.depth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitgetDepthArg {

    private String instType;

    private String channel;

    private String instId;
}
