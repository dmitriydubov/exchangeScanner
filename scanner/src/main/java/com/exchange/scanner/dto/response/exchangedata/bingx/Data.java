package com.exchange.scanner.dto.response.exchangedata.bingx;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private List<Symbols> symbols;
}
