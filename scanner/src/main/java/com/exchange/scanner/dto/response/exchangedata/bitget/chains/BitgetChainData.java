package com.exchange.scanner.dto.response.exchangedata.bitget.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitgetChainData {

    private String coin;

    private List<BitgetChain> chains;
}
