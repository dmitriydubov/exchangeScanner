package com.exchange.scanner.dto.response.exchangedata.bybit.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitChainsResult {

    private List<BybitChainsRows> rows;
}