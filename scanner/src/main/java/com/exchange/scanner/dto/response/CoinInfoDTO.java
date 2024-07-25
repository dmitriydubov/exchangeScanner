package com.exchange.scanner.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinInfoDTO {

    private String coin;

    private String slug;

    private String coinMarketCapLink;

    private String logoLink;
}
