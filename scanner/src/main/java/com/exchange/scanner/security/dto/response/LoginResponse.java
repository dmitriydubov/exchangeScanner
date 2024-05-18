package com.exchange.scanner.security.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponse {

    private Long id;

    private String token;

    private String refreshToken;

    private String username;

    private List<String> roles;
}
