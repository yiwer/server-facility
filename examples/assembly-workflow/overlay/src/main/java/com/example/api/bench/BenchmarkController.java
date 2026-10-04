package com.example.api.bench;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bench")
final class BenchmarkController {
    @GetMapping("/hello") Readiness hello() { return new Readiness("ready"); }

    record Readiness(String message) {}
}
