package com.agent.gateway.proxy.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthzRequest {
    private String branchCustomerNumber;
    private List<String> gvoEntitlementsList;
    private List<String> businessTransactions;
    private List<String> serviceShopTransactions;
}
