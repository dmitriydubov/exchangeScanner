package com.exchange.scanner.dto.response.exchangedata.gateio.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GateIOCoinDepth {

    private String event;

    private GateIOCoinDepthResult result;
}
