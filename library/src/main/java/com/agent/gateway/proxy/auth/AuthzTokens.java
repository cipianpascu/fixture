package com.agent.gateway.proxy.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthzTokens {
    @JsonProperty("authorizationToken")
    private String authorizationToken;

    @JsonProperty("eidpAccessToken")
    private String eidpAccessToken;

    @JsonProperty("customerAccessToken")
    private String customerAccessToken;

    @JsonProperty("disallowedServiceShopTransactions")
    private List<String> disallowedServiceShopTransactions;
}
