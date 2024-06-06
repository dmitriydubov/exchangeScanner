package com.exchange.scanner.dto.response.exchangedata.xt.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTTicker {

    private List<XTTickerResult> result;
}
