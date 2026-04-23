package com.agent.gateway.proxy.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthTokens {
    private String serviceToken;
    private String userGrantsToken;
}
