package com.exchange.scanner.dto.response.exchangedata.bitmart.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class Currencies {

    private List<Symbols> symbols;
}
