package com.exchange.scanner.dto.response;

import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ChainResponseDTO {

    private String exchange;

    private Coin coin;

    private Set<Chain> chains;
}

