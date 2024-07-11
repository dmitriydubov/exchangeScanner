package com.exchange.scanner.dto.response.event;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArbitrageEvent {

    private String coin;

    private String coinMarketCapLink;

    private String coinMarketCapLogo;

    private List<EventData> eventData;
}
