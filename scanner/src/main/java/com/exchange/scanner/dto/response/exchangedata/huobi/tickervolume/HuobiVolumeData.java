package com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiVolumeData {

    private String symbol;

    private String vol;
}
