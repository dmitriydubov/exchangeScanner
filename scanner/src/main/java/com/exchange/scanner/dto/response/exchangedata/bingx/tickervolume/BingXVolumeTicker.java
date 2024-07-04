package com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BingXVolumeTicker {

    private List<BingXVolumeTickerData> data;
}
