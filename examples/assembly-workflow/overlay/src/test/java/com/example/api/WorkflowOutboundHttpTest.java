package com.example.api;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowOutboundHttpTest {
 @Test void propagatesTheHostObservationAndBuilderPolicy() throws Exception {
  try(var upstream=new Upstream();var issuer=new TestIssuer();var app=new RunningApp(issuer,new Class<?>[]{HostPolicy.class},
       "--bench.upstream.base-url="+upstream.base(),"--bench.upstream.credential=upstream-only","--management.tracing.sampling.probability=1")) {
   assertThat(app.get("/api/bench/stock?sku=OK",issuer.token(),"traceparent","00-0123456789abcdef0123456789abcdef-1234567890abcdef-01").statusCode()).isEqualTo(200);
   assertThat(upstream.trace.get()).matches("00-0123456789abcdef0123456789abcdef-[0-9a-f]{16}-01")
       .doesNotContain("-1234567890abcdef-");
   assertThat(upstream.host.get()).isEqualTo("configured");
   assertThat(upstream.authorization.get()).isEqualTo("Bearer upstream-only");
   assertThat(upstream.admissions.get()).isEqualTo(1);
  }
 }
 @Test void timesOutAnActualBodyReadAndReusesTheClient() throws Exception {
  try(var upstream=new Upstream();var issuer=new TestIssuer();var app=new RunningApp(issuer,
       "--bench.upstream.base-url="+upstream.base(),"--bench.upstream.credential=upstream-only")) {
   String token=issuer.token();assertThat(app.get("/api/bench/hello",token).statusCode()).isEqualTo(200);
   long start=System.nanoTime();var response=app.get("/api/bench/stock?sku=SLOW",token);
   assertThat(response.statusCode()).isEqualTo(504);
   assertThat(response.body()).contains("upstream_timeout").doesNotContain("upstream-only");
   assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1250);
   assertThat(upstream.prefixSent.getCount()).isZero();
   assertThat(upstream.release.getCount()).isEqualTo(1);
   assertThat(app.get("/api/bench/stock?sku=OK",token).statusCode()).isEqualTo(200);
   assertThat(upstream.admissions.get()).isEqualTo(2);
  }
 }
 @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
 static class HostPolicy {
  @org.springframework.context.annotation.Bean org.springframework.boot.restclient.RestClientCustomizer hostPolicy() {
   return builder -> builder.defaultHeader("X-Workflow-Host","configured");
  }
 }
 @Test void refusesActualOversizeBodyAndReusesTheClient() throws Exception {
  try (var upstream = new Upstream(); var issuer = new TestIssuer();
       var app = new RunningApp(issuer, "--bench.upstream.base-url="+upstream.base(), "--bench.upstream.credential=upstream-only")) {
   var response = app.get("/api/bench/stock?sku=LARGE",issuer.token());
   assertThat(response.statusCode()).isEqualTo(502);
   assertThat(response.body()).contains("upstream_invalid_response").doesNotContain("upstream-only");
   assertThat(upstream.admissions.get()).isEqualTo(1);
   assertThat(upstream.prefixSent.getCount()).isZero();
   assertThat(upstream.release.getCount()).isEqualTo(1);
   var healthy = app.get("/api/bench/stock?sku=OK",issuer.token());
   assertThat(healthy.statusCode()).isEqualTo(200);
   assertThat(healthy.body()).isEqualTo("{\"sku\":\"OK\",\"available\":23}");
   assertThat(upstream.admissions.get()).isEqualTo(2);
  }
 }
 static final class Upstream implements AutoCloseable {
  final HttpServer server; final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
  final AtomicInteger admissions=new AtomicInteger();
  final CountDownLatch prefixSent=new CountDownLatch(1),release=new CountDownLatch(1);
  final AtomicReference<String> trace=new AtomicReference<>(),host=new AtomicReference<>(),authorization=new AtomicReference<>();
  Upstream() throws Exception {
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(workers);
   server.createContext("/inventory/", exchange -> {
    admissions.incrementAndGet(); trace.set(exchange.getRequestHeaders().getFirst("traceparent")); host.set(exchange.getRequestHeaders().getFirst("X-Workflow-Host")); authorization.set(exchange.getRequestHeaders().getFirst("Authorization")); String sku=exchange.getRequestURI().getPath().substring("/inventory/".length());
    byte[] bytes=("{\"sku\":\""+sku+"\",\"available\":23}"+(sku.equals("LARGE")?" ".repeat(32768):"")).getBytes(StandardCharsets.UTF_8);
    try(exchange) { exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,0); 
     if(sku.equals("SLOW") || sku.equals("LARGE")) {
      exchange.getResponseBody().write(sku.equals("SLOW")?new byte[]{123}:bytes);exchange.getResponseBody().flush();prefixSent.countDown();
      try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("fixture release deadline"); }
      catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
     } else exchange.getResponseBody().write(bytes);
    }
   }); server.start();
  }
  String base(){return "http://127.0.0.1:"+server.getAddress().getPort();}
  public void close(){release.countDown();server.stop(0);workers.shutdownNow();try{assertThat(workers.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
 }
}
