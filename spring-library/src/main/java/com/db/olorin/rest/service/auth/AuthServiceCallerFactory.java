package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.service.TlsContextFactory;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthServiceCallerFactory {
    private final AuthServiceConfigRegistry registry; private final TlsContextFactory tls; private final CloudRunIdTokenProvider tokens;
    private final ConcurrentHashMap<String,AuthServiceCaller> callers=new ConcurrentHashMap<>();
    public AuthServiceCallerFactory(AuthServiceConfigRegistry registry,TlsContextFactory tls,CloudRunIdTokenProvider tokens){this.registry=registry;this.tls=tls;this.tokens=tokens;}
    public AuthServiceCaller get(String name){return callers.computeIfAbsent(name,key->new AuthServiceCaller(registry.get(key),tls,tokens));}
    public ResolvedAuthServiceConfig getConfig(String name){return registry.get(name);}
}
