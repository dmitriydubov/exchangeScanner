package com.exchange.scanner.dto.response.exchangedata.bitget.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetTickerVolume {

    private List<BitgetTickerVolumeData> data;
}
