package com.exchange.scanner.dto.response.exchangedata.lbank.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LBankDepthData {

    private List<List<String>> asks;

    private List<List<String>> bids;

}
