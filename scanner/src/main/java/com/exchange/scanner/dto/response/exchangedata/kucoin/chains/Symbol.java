package com.exchange.scanner.dto.response.exchangedata.kucoin.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Symbol {

    private String currency;

    private List<Chain> chains;
}
