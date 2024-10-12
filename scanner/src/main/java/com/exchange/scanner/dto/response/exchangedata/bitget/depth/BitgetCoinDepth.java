package com.exchange.scanner.dto.response.exchangedata.bitget.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetCoinDepth {

    private String action;

    private BitgetDepthArg arg;

    private List<BitgetDepthData> data;
}
