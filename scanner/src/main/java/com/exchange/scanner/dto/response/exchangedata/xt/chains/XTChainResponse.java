package com.exchange.scanner.dto.response.exchangedata.xt.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTChainResponse {

    private List<XTChainResult> result;
}
