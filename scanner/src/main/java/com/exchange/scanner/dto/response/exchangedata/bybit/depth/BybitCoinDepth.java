package com.exchange.scanner.dto.response.exchangedata.bybit.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BybitCoinDepth {

    @JsonIgnore
    private Integer retCode;

    @JsonIgnore
    private String retMessage;

    @JsonIgnore
    private String coinName;

    private Result result;

    @JsonIgnore
    private List<String> retExtInfo;

    @JsonIgnore
    private Long time;
}
