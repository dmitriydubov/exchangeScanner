package com.exchange.scanner.dto.response.exchangedata.probit.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProbitCoinDepth {

    private List<ProbitDepthData> data;
}
