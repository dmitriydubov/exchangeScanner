package com.exchange.scanner.dto.response.exchangedata.xt.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTDepthData {

    private String s;

    private List<List<String>> b;

    private List<List<String>> a;
}
