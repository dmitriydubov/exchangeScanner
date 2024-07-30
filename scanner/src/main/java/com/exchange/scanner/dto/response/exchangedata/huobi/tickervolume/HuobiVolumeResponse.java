package com.exchange.scanner.dto.response.exchangedata.huobi.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiVolumeResponse {

    private List<HuobiVolumeData> data;
}
