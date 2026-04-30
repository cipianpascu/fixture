package com.agent.gateway.proxy.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRequest {
    private List<String> sparteGvo;
    private List<String> btx;
    private List<String> pss;
}
