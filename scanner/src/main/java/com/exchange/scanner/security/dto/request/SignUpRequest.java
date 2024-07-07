package com.exchange.scanner.security.dto.request;

import com.exchange.scanner.security.model.Role;

import java.util.Set;

public record SignUpRequest(String username, String email, String telegram, String password, Set<Role> roles) {}
