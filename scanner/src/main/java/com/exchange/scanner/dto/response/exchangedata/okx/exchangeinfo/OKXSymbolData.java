package com.exchange.scanner.dto.response.exchangedata.okx.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXSymbolData {

    private List<Symbols> data;
}
