package com.exchange.scanner.dto.response.exchangedata.lbank.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankSymbolData {

    private List<Symbols> data;
}
