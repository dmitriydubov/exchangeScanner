package com.exchange.scanner.dto.response.exchangedata.probit.depth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProbitCoinDepth {

    @JsonProperty(value = "market_id")
    private String marketId;

    @JsonProperty(value = "order_books_l1")
    private List<ProbitDepthData> orderBooksL1;
}
