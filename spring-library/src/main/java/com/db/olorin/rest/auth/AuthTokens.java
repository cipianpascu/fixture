package com.db.olorin.rest.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthTokens {
    @JsonProperty("glue_token")
    private String glueToken;

    @JsonProperty("auth_z_token")
    private String authZToken;

    @JsonProperty("customer_access_token")
    private String customerAccessToken;

    @JsonProperty("disallowed_pss")
    private List<String> disallowedPss;
}
