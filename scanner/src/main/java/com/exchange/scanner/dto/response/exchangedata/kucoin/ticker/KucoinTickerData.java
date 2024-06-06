package com.exchange.scanner.dto.response.exchangedata.kucoin.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KucoinTickerData {

    private List<KucoinTicker> ticker;
}
