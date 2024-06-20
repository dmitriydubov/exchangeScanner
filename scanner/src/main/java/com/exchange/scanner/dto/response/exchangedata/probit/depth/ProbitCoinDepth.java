package com.exchange.scanner.dto.response.exchangedata.probit.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProbitCoinDepth {

    @JsonIgnore
    private String coinName;

    private List<Data> data;
}
