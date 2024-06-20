package com.exchange.scanner.dto.response.exchangedata.probit.depth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Data {

    private String side;

    private String price;

    private String quantity;
}
