package com.db.olorin.rest.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonProperty("glueAccessToken")
    @JsonAlias("eidpAccessToken")
    private String glueAccessToken;

    @JsonProperty("customerAccessToken")
    private String customerAccessToken;

    @JsonProperty("allowedServiceShopTransactions")
    @JsonAlias("disallowedServiceShopTransactions")
    private List<String> allowedServiceShopTransactions;
}
