package com.exchange.scanner.dto.response.event;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.TreeSet;

@Getter
@Setter
public class UserTradeEvent {

    private TreeSet<UserBuyTradeEventDTO> buyTradeEventDTO;

    private TreeSet<UserSellTradeEventDTO> sellTradeEventDTO;
}
