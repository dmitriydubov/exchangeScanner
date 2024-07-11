package com.exchange.scanner.dto.response.event;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserTradeEvent {

    private Set<UserBuyTradeEventDTO> buyTradeEventDTO;

    private Set<UserSellTradeEventDTO> sellTradeEventDTO;
}
