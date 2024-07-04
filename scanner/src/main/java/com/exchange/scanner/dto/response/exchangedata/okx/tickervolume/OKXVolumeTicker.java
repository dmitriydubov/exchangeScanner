package com.exchange.scanner.dto.response.exchangedata.okx.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXVolumeTicker {

    private List<OKXDataVolumeTicker> data;
}
