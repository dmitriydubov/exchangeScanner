package com.exchange.scanner.dto.response.exchangedata.bitget.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private String coin;

    private List<Chain> chains;
}
