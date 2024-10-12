package com.exchange.scanner.dto.response.exchangedata.huobi.depth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiCoinDepth {

    private String ch;

    private HuobiDepthTick tick;
}
