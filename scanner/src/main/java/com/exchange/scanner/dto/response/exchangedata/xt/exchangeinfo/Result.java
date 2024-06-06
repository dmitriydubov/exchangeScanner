package com.exchange.scanner.dto.response.exchangedata.xt.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Result {

    private List<Symbols> symbols;
}
