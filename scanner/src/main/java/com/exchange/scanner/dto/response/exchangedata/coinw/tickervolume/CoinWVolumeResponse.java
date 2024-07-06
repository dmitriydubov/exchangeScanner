package com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CoinWVolumeResponse {

    private Map<String, CoinWVolumeTickerData> data;
}
