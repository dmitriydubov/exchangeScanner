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

    private List<EventData> eventData;
}
