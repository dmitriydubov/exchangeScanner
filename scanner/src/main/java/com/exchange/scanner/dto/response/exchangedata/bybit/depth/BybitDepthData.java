package com.exchange.scanner.dto.response.exchangedata.bybit.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitDepthData {

    private String s;

    private List<List<String>> b;

    private List<List<String>> a;
}
