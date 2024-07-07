package com.exchange.scanner.dto.response.exchangedata.xt.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTVolumeTicker {

    private List<XTVolumeTickerResult> result;
}
