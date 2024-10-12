package com.exchange.scanner.dto.response.exchangedata.kucoin.websocket;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WebsocketPublicKeyData {

    private String token;

    private List<WebsocketPublicKeyInstance> instanceServers;
}
