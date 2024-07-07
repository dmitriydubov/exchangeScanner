package com.exchange.scanner.dto.response.exchangedata.okx.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OKXDataVolumeTicker {

    private String instType;

    private String volCcy24h;
}
