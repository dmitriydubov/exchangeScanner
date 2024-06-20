package com.exchange.scanner.dto.response.exchangedata.coinex.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Depth {

    private List<List<String>> asks;

    private List<List<String>> bids;

    @JsonIgnore
    private Long checksum;

    @JsonIgnore
    private String last;

    @JsonProperty("updated_at")
    @JsonIgnore
    private Long updatedAt;
}
