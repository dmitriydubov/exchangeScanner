package com.exchange.scanner.dto.response.exchangedata.coinex.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinExVolumeTicker {

    private List<CoinExVolumeTickerData> data;
}
