package com.exchange.scanner.dto.response.exchangedata.bybit.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitDepthResult {

    private List<List<String>> bids;

    private List<List<String>> asks;
}
