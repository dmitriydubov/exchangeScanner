package com.exchange.scanner.services.utils;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.util.concurrent.TimeUnit;

public class WebClientBuilder {

    private static final int MEMORY_SIZE = 16 * 1024 * 1024;

    public static WebClient buildWebClient(String baseEndpoint, int timeout) {
        HttpClient httpClient = HttpClient.create()
//                .proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP)).host("localhost").port(1090)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS));
                });

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config
                        .defaultCodecs()
                        .maxInMemorySize(MEMORY_SIZE)
                )
                .build();

        return WebClient.builder()
                .baseUrl(baseEndpoint)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
