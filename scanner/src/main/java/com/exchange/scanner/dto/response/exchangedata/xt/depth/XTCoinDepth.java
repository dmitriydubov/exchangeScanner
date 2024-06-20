package com.exchange.scanner.dto.response.exchangedata.xt.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class XTCoinDepth {

    @JsonIgnore
    private Integer rc;

    @JsonIgnore
    private String mc;

    @JsonIgnore
    private List<String> ma;

    @JsonIgnore
    private String coinName;

    private Result result;
}
