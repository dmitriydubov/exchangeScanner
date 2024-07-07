package com.exchange.scanner.dto.response.exchangedata.huobi.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HuobiChainsResponse {

    private List<HuobiChainsData> data;
}
