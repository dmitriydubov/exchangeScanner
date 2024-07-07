package com.exchange.scanner.dto.response.exchangedata.coinex.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinExVolumeTickerData {

    private String market;

    private String value;
}
