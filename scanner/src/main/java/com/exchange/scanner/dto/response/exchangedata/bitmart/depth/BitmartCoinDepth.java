package com.exchange.scanner.dto.response.exchangedata.bitmart.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitmartCoinDepth {

    private List<BitmartDepthData> data;
}
