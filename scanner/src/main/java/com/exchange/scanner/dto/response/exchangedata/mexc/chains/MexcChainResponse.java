package com.exchange.scanner.dto.response.exchangedata.mexc.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MexcChainResponse {

    private String coin;

    private List<NetworkList> networkList;
}
