package com.exchange.scanner.dto.response.exchangedata.okx.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OKXSymbol {

    private String baseCcy;

    private String quoteCcy;

    private String state;
}
