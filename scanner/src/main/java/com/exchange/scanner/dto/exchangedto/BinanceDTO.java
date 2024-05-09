package com.exchange.scanner.dto.exchangedto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinanceDTO {

    private String symbol;

    private String priceChange;

    private String priceChangePercent;

    private String weightedAvgPrice;

    private String prevClosePrice;

    private String lastPrice;

    private String lastQty;

    private String openPrice;

    private String highPrice;

    private String lowPrice;

    private String volume;

    private String quoteVolume;

    private String openTime;

    private String closeTime;

    private String firstId;

    private String lastId;

    private String count;
}
