package com.exchange.scanner.dto.response.exchangedata.bingx.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BingXChainResponse {

    private String code;


    private List<BingXChainData> data;
}
