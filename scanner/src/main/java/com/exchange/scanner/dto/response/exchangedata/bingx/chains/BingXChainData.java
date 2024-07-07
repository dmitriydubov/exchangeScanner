package com.exchange.scanner.dto.response.exchangedata.bingx.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BingXChainData {

    private String coin;

    private List<BingXNetwork> networkList;
}
