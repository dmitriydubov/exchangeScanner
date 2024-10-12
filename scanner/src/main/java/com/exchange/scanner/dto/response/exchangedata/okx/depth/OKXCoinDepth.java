package com.exchange.scanner.dto.response.exchangedata.okx.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXCoinDepth {

    private OKXDepthArg arg;

    private List<OKXDepthData> data;
}
