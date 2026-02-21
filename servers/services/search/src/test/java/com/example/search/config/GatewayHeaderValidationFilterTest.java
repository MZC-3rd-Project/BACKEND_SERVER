package com.example.search.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeaderValidationFilterTest {

    @Test
    void doFilter_skipsActuatorWithoutHeaders() throws ServletException, IOException {
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_allowsInternalApiWithoutHeaders() throws ServletException, IOException {
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/search/indexes/recreate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_allowsInternalApiWithAnyGatewayHeaders() throws ServletException, IOException {
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/search/indexes/recreate");
        request.addHeader("X-Gateway-Auth", "wrong-token");
        request.addHeader("X-User-Id", "101");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_skipsExternalApiPath() throws ServletException, IOException {
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
