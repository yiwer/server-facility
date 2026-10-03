package com.example.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.example.api.greeting.Actor;
import com.example.api.greeting.Greetings;

@RestController
class GreetingController {
    private final Greetings greetings = new Greetings();
    @GetMapping("/health") Map<String, String> health() { return Map.of("status", "UP"); }
    @GetMapping("/api/greeting") Greetings.Greeting greeting(@AuthenticationPrincipal Jwt jwt) {
        return greetings.greet(new Actor(jwt.getClaimAsString("iss"), jwt.getSubject()));
    }
}
