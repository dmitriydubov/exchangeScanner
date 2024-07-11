package com.exchange.scanner.services.utils.AppUtils;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogsUtils {

    public static void createErrorResumeLogs(Throwable error, String exchangeName) {
        if (error instanceof ReadTimeoutException) {
            log.error("Превышен лимит ожидания ответа от {}. Причина: {}.", exchangeName, error.getLocalizedMessage());
        } else {
            log.error("Ошибка при запросе к {}. Причина: {}.", exchangeName, error.getLocalizedMessage());
        }
    }
}
