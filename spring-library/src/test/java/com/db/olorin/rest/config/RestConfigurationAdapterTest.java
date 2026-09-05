package com.db.olorin.rest.config;

import com.db.olorin.core.configuration.ConfigurationIF;
import com.db.olorin.core.configuration.ConfigurationItemIF;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestConfigurationAdapterTest {
    @Test
    void readsIndexedConfigurationListsWithoutChangingGatewayReferences() {
        ProxyProperties.BackendDefinition backend = RestConfigurationAdapter.from(configuration(Map.of(
            "gateway.backends[0].name", "product-backend",
            "gateway.backends[0].baseUrl", "https://products.example",
            "gateway.backends[0].proxy.host", "proxy.example",
            "gateway.backends[0].proxy.non-proxy-hosts[1]", "*.internal",
            "gateway.backends[0].proxy.non-proxy-hosts[0]", "localhost",
            "gateway.backends[0].auth-request.sparte-gvo[0]", "gvo-a",
            "gateway.backends[0].auth-request.sparte-gvo[1]", "gvo-b"
        ))).proxyProperties().backends().getFirst();

        assertThat(backend.proxy().orElseThrow().nonProxyHosts()).containsExactly("localhost", "*.internal");
        assertThat(backend.authRequest().orElseThrow().sparteGvo().orElseThrow()).containsExactly("gvo-a", "gvo-b");
    }

    @Test
    void readsOptInForwardedHeaderAndCookieLists() {
        ProxyProperties.BackendDefinition backend = RestConfigurationAdapter.from(configuration(Map.of(
            "gateway.backends[0].name", "products", "gateway.backends[0].baseUrl", "https://products.example",
            "gateway.backends[0].forward-headers[0]", "x-asm-rctoken",
            "gateway.backends[0].forward-cookies", "session, locale"
        ))).proxyProperties().backends().getFirst();
        assertThat(backend.forwardHeaders()).containsExactly("x-asm-rctoken");
        assertThat(backend.forwardCookies()).containsExactly("session", "locale");
    }

    @Test
    void readsExistingGatewayKeysIncludingBackendMapsAndHistoryGenerators() {
        ProxyProperties properties = RestConfigurationAdapter.from(configuration(Map.of(
            "gateway.backends[0].name", "product-backend",
            "gateway.backends[0].baseUrl", "https://products.example",
            "gateway.backends[0].timeout", "2s",
            "gateway.backends[0].securityType", "basic",
            "gateway.backends[0].securityConfig.username", "alice",
            "gateway.backends[0].securityConfig.password", "secret",
            "gateway.backends[0].history.generated-headers.X-Request-Id", "generator:uuid",
            "gateway.backends[0].history.additional-properties.correlationId", "generator:uuid"
        ))).proxyProperties();

        ProxyProperties.BackendDefinition backend = properties.backends().getFirst();
        assertThat(backend.name()).isEqualTo("product-backend");
        assertThat(backend.timeout()).hasToString("PT2S");
        assertThat(backend.securityConfig()).containsEntry("username", "alice");
        assertThat(backend.history().orElseThrow().generatedHeaders())
            .containsEntry("X-Request-Id", "generator:uuid");
        assertThat(backend.history().orElseThrow().additionalProperties())
            .containsEntry("correlationId", "generator:uuid");
    }

    private ConfigurationIF configuration(Map<String, String> values) {
        List<ConfigurationItemIF> items = values.entrySet().stream()
            .map(entry -> new Item(entry.getKey(), entry.getValue())).map(ConfigurationItemIF.class::cast).toList();
        return new ConfigurationIF() {
            public String getName() { return RestConfigurationAdapter.SECTION_NAME; }
            public List<ConfigurationItemIF> getConfigurationItems() { return items; }
            public List<ConfigurationItemIF> getConfigurationItemsByGroup(String group) { return items.stream().filter(item -> item.getName().startsWith(group)).toList(); }
            public ConfigurationItemIF getConfigurationItem(String name) { return items.stream().filter(item -> item.getName().equals(name)).findFirst().orElse(null); }
            public ConfigurationItemIF getConfigurationItemOrDefault(String name, String defaultValue) { ConfigurationItemIF item=getConfigurationItem(name); return item == null ? new Item(name, defaultValue) : item; }
        };
    }

    private static final class Item implements ConfigurationItemIF {
        private String name; private String value;
        Item(String name, String value) { this.name=name; this.value=value; }
        public String getName(){return name;} public String getValue(){return value;} public void setName(String value){name=value;} public void setValue(String value){this.value=value;}
        public void setValue(boolean value){setValue(Boolean.toString(value));} public void setValue(int value){setValue(Integer.toString(value));} public void setValue(long value){setValue(Long.toString(value));} public void setValue(double value){setValue(Double.toString(value));}
        public String getValueOrDefaultIfNull(String fallback){return value == null ? fallback:value;} public boolean getBooleanValue(){return Boolean.parseBoolean(value);} public int getIntValue(){return Integer.parseInt(value);} public long getLongValue(){return Long.parseLong(value);} public double getDoubleValue(){return Double.parseDouble(value);}
        public boolean getBooleanValueOrDefaultIfNull(boolean fallback){return value==null?fallback:getBooleanValue();} public int getIntValueOrDefaultIfNull(int fallback){return value==null?fallback:getIntValue();} public long getLongValueOrDefaultIfNull(long fallback){return value==null?fallback:getLongValue();} public double getDoubleValueOrDefaultIfNull(double fallback){return value==null?fallback:getDoubleValue();}
    }
}
