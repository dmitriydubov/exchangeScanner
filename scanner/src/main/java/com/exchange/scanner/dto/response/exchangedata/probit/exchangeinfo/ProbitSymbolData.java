package com.exchange.scanner.dto.response.exchangedata.probit.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class ProbitSymbolData {

    private List<Symbols> data;
}