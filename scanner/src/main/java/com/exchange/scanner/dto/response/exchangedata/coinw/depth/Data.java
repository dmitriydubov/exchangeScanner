package com.exchange.scanner.dto.response.exchangedata.coinw.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private List<Bid> bids;

    private List<Ask> asks;
}
