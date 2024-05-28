package com.exchange.scanner.dto.response.exchangedata;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExchangeDataResponse {

    private List<CoinEvent> coinEvent;

}
