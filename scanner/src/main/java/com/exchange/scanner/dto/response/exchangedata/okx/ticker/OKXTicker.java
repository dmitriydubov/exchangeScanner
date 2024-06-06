package com.exchange.scanner.dto.response.exchangedata.okx.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXTicker {

    private List<OKXDataTicker> data;
}
