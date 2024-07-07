package com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KucoinTickerData {

    private String symbol;

    private String volValue;
}
