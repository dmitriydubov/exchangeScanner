package com.exchange.scanner.handler;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponseBody {

    private String message;

    private String description;
}
