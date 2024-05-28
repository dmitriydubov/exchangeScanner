package com.exchange.scanner.dto.response.exchangedata;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CoinEvent {

    private String coin;

    private String buyExchange;

    private String sellExchange;

    private String profitVolumePrice;

    private String profitVolumeCoins;

    private String priceBuy;

    private String priceSell;

    //Это упрощенный объект ответа. Будет дорабатываться
}
