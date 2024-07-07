package com.exchange.scanner.dto.response.exchangedata.xt.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTDepthResult {

    private List<List<String>> bids;

    private List<List<String>> asks;
}
