package com.exchange.scanner.dto.response.exchangedata.okx.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class OKXDepthData {

    private List<List<String>> asks;

    private List<List<String>> bids;
}