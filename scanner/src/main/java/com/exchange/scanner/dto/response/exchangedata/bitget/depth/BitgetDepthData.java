package com.exchange.scanner.dto.response.exchangedata.bitget.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetDepthData {

    private List<List<String>> asks;

    private List<List<String>> bids;
}
