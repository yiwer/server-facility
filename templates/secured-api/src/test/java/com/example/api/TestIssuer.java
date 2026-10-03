package com.example.api;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

/** Controlled external authority fixture: JDK signing is independent of the production Nimbus verifier. */
final class TestIssuer implements AutoCloseable {
    final HttpServer server;
    final java.util.concurrent.ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    final KeyPair a = key(), b = key();
    final AtomicInteger metadataRequests = new AtomicInteger(), keyRequests = new AtomicInteger();
    volatile List<String> published = List.of("a");
    volatile int keyStatus = 200;
    volatile String keyBody;
    volatile String discoveredJwkUri;
    volatile String algorithm = "RS256";
    volatile java.util.concurrent.CountDownLatch keyEntered, keyRelease;
    private final JsonMapper mapper = JsonMapper.builder().build();

    TestIssuer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/", exchange -> {
            byte[] bytes;
            int status = 200;
            if (exchange.getRequestURI().getPath().equals("/keys")) {
                keyRequests.incrementAndGet();
                if (keyEntered != null) keyEntered.countDown();
                if (keyRelease != null) try { keyRelease.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                status = keyStatus;
                bytes = keyBody == null ? jwkDocument().getBytes(StandardCharsets.UTF_8)
                        : keyBody.getBytes(StandardCharsets.UTF_8);
            } else {
                metadataRequests.incrementAndGet();
                bytes = mapper.writeValueAsBytes(Map.of("issuer", issuer(), "jwks_uri", discoveredJwkUri == null ? issuer() + "/keys" : discoveredJwkUri));
            }
            try (exchange) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
    }

    String issuer() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    String jwkDocument() { return mapper.writeValueAsString(Map.of("keys", published.stream().map(this::jwk).toList())); }
    String token() throws Exception { return token("a", Map.of(), Set.of()); }
    String token(String kid, Map<String, Object> overrides, Set<String> removed) throws Exception {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", issuer()); claims.put("aud", List.of("secured-api")); claims.put("sub", "alice");
        claims.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.put("nbf", Instant.now().minusSeconds(5).getEpochSecond()); claims.put("scope", "greeting:read");
        claims.putAll(overrides); removed.forEach(claims::remove);
        String unsigned = encoded(mapper.writeValueAsBytes(Map.of("alg", algorithm, "typ", "JWT", "kid", kid))) + "."
                + encoded(mapper.writeValueAsBytes(claims));
        Signature signature = Signature.getInstance(algorithm.equals("RS512") ? "SHA512withRSA" : "SHA256withRSA");
        signature.initSign((kid.equals("b") ? b : a).getPrivate());
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + encoded(signature.sign());
    }
    private Map<String, String> jwk(String id) {
        RSAPublicKey publicKey = (RSAPublicKey) (id.equals("b") ? b : a).getPublic();
        return Map.of("kty", "RSA", "use", "sig", "alg", algorithm, "kid", id,
                "n", encoded(unsigned(publicKey.getModulus().toByteArray())),
                "e", encoded(unsigned(publicKey.getPublicExponent().toByteArray())));
    }
    private static byte[] unsigned(byte[] bytes) { return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes; }
    private static String encoded(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static KeyPair key() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void close() { if (keyRelease != null) keyRelease.countDown(); server.stop(0); workers.close(); }
}
