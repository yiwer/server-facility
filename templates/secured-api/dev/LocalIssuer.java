import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Disposable local fixture, never packaged: no login, token endpoint, refresh endpoint or persistent key. */
class LocalIssuer {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply a new local state directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectory(output);
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair key = generator.generateKeyPair();
        RSAPublicKey rsa = (RSAPublicKey) key.getPublic();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        try (var executor = Executors.newFixedThreadPool(2)) {
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
            String jwk = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"local\",\"n\":\""
                    + unsigned(rsa.getModulus().toByteArray()) + "\",\"e\":\"" + unsigned(rsa.getPublicExponent().toByteArray()) + "\"}]}";
            String metadata = "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/keys\"}";
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    boolean supported = path.equals("/keys") || path.equals("/.well-known/openid-configuration") || path.equals("/.well-known/oauth-authorization-server");
                    byte[] body = (supported ? path.equals("/keys") ? jwk : metadata : "{}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(supported && exchange.getRequestMethod().equals("GET") ? 200 : 404, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
            try {
                Files.writeString(output.resolve("local.properties"), "spring.profiles.active=local\n"
                        + "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer + "\n"
                        + "spring.security.oauth2.resourceserver.jwt.audiences=secured-api\n"
                        + "app.security.resource-uri=http://127.0.0.1:8080\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.writeString(output.resolve("token.txt"), token(key, issuer, "greeting:read notes:read notes:write"), StandardOpenOption.CREATE_NEW);
                Files.writeString(output.resolve("no-scope-token.txt"), token(key, issuer, "unrelated:read"), StandardOpenOption.CREATE_NEW);
                System.out.println("LOCAL_FIXTURE_READY " + output);
                // Fixed ten-minute lifetime; restarting creates a fresh ephemeral key and tokens.
                new CountDownLatch(1).await(10, TimeUnit.MINUTES);
            } finally { server.stop(0); }
        }
    }
    private static String token(KeyPair key, String issuer, String scope) throws GeneralSecurityException {
        long now = Instant.now().getEpochSecond();
        String header = encoded("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"local\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoded(("{\"iss\":\"" + issuer + "\",\"aud\":[\"secured-api\"],\"sub\":\"local-demo\",\"exp\":"
                + (now + 600) + ",\"nbf\":" + now + ",\"scope\":\"" + scope + "\"}").getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + payload;
        var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key.getPrivate());
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII)); return unsigned + "." + encoded(signature.sign());
    }
    private static String unsigned(byte[] value) { return encoded(value[0] == 0 ? Arrays.copyOfRange(value, 1, value.length) : value); }
    private static String encoded(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
