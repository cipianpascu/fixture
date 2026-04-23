package com.agent.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminCorsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void optionsAdminBackendsReturnsCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, "https://example-frontend.web.app");
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization");

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/admin/api/backends",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("https://example-frontend.web.app",
                response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(response.getHeaders().getAccessControlAllowMethods().stream()
                .anyMatch(method -> method.matches("POST")));
        assertTrue(response.getHeaders().getAccessControlAllowHeaders().stream()
                .anyMatch(header -> header.equalsIgnoreCase("content-type")));
    }
}
