package com.exchange.scanner.dto.response.exchangedata.okx.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXChainsResponse {

    private String code;

    private String msg;

    private List<OKXChainData> data;
}
