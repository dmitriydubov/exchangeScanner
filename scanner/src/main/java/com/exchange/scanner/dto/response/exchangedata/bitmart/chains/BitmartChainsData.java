package com.exchange.scanner.dto.response.exchangedata.bitmart.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitmartChainsData {

    private List<BitmartChainsCurrencies> currencies;
}
