package com.exchange.scanner.dto.response.exchangedata.lbank.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankVolumeTickerResponse {

    private List<LBankVolumeTickerData> data;
}
