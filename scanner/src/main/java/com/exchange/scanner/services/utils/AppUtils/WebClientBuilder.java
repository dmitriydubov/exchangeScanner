package com.exchange.scanner.services.utils.AppUtils;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WebClientBuilder {

    private static final int MEMORY_SIZE = 16 * 1024 * 1024;

    /**
     * Создание WebClient для внешних api запросов
     * @param baseEndpoint базовый эндпоинт биржи
     * @param timeout тайм-аут запроса
     * */

    public static WebClient buildWebClient(String baseEndpoint, int timeout) {
        HttpClient httpClient = HttpClient.create()
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
                .filter(logRequest(false))
                .baseUrl(baseEndpoint)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Метод логгирует информацию о запросе
     *
     * @return ExchangeFilterFunction возвращает функцию для метода filter() WebClient.builder()
     *
     * @param isLogging параметр, который указывает включено ли логгирование. Задаётся в методе filter() WebClient.builder()
     * */

    private static ExchangeFilterFunction logRequest(boolean isLogging) {
        if (isLogging) {
            return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
                clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info("{}={}", name, value)));
                return Mono.just(clientRequest);
            });
        }
        return ExchangeFilterFunction.ofRequestProcessor(Mono::just);
    }
}
