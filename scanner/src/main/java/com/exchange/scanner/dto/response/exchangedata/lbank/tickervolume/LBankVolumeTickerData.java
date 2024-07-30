package com.exchange.scanner.dto.response.exchangedata.lbank.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankVolumeTickerData {

    private String symbol;

    private LBankVolumeTicker ticker;
}
