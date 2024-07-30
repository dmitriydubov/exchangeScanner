package com.exchange.scanner.dto.response.exchangedata.bitmart.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitmartVolumeTicker {

    private List<List<String>> data;
}
