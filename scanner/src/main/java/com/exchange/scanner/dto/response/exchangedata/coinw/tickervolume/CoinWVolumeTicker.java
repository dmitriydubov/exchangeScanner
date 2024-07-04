package com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinWVolumeTicker {

    private List<CoinWVolumeTickerData> data;
}
