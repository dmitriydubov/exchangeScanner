package com.exchange.scanner.dto.response.exchangedata.huobi.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiDepthTick {

    private List<List<String>> bids;

    private List<List<String>> asks;
}
