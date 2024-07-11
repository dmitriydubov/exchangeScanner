package com.exchange.scanner.dto.response;

import com.exchange.scanner.model.Coin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinInfoDTO {

    private String exchange;

    private Coin coin;

    private String slug;

    private String coinMarketCapLink;

    private String logoLink;
}
