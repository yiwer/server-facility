package com.example.fixtures;

import cn.code91.facility.web.ratelimit.RateLimit;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration(proxyBeanMethods = false)
public class PrincipalQuotaFixture {
    @RestController public static class Operation {
        @GetMapping("/api/greeting/quota")
        @RateLimit(scope = RateLimit.Scope.PRINCIPAL, capacity = 1, permitsPerSecond = 0.000001)
        public Map<String, String> operation(jakarta.servlet.http.HttpServletRequest request) {
            return Map.of("actor", request.getUserPrincipal().getName());
        }
    }
}
