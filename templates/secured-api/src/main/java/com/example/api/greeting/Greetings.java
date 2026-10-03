package com.example.api.greeting;

import java.util.Objects;

/** Small authenticated application use case; persistence is introduced separately. */
public final class Greetings {
    public record Greeting(Actor actor, String message) {}
    public Greeting greet(Actor actor) { return new Greeting(Objects.requireNonNull(actor, "actor"), "Hello"); }
}
