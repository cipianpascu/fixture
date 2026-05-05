package com.agent.gateway.proxy;

import com.agent.gateway.proxy.service.auth.GoogleCloudRunIdTokenProvider;
import io.quarkus.test.junit.QuarkusMock;
import org.junit.jupiter.api.BeforeAll;

abstract class AbstractProxyQuarkusTest {

    @BeforeAll
    static void installCloudRunTokenProviderMock() {
        QuarkusMock.installMockForType(
            new GoogleCloudRunIdTokenProvider() {
                @Override
                public String getIdToken(String audience) {
                    return "test-id-token-for:" + audience;
                }
            },
            GoogleCloudRunIdTokenProvider.class
        );
    }
}
