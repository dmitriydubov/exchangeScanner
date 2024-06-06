package com.exchange.scanner.dto.response.exchangedata.okx.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OKXDataTicker {

    private String instId;

    private String vol24h;

    private String bidPx;

    private String askPx;
}
