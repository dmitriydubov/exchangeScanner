package com.exchange.scanner.dto.response.exchangedata.okx.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OKXCoinDepth {

    @JsonIgnore
    private String code;

    @JsonIgnore
    private String msg;

    @JsonIgnore
    private String coinName;

    private List<Data> data;
}
