package com.example.api;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class WorkflowAdmissionHttpTest {
 @TempDir Path staging;
 @Test void refusesAFileAndFormPartWithTheSameName() throws Exception {
  try (var issuer=new TestIssuer(); var app=new RunningApp(issuer, "--bench.staging-directory="+staging)) {
   String boundary="mixed-file-form";
   String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"ok.csv\"\r\nContent-Type: text/csv\r\n\r\nname,quantity\nplain,2\n\r\n"
       +"--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\nsecond-part\r\n--"+boundary+"--\r\n";
   var response=app.client.send(HttpRequest.newBuilder(URI.create(app.base+"/api/bench/import"))
       .header("Authorization","Bearer "+issuer.token()).header("Content-Type","multipart/form-data; boundary="+boundary)
       .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
   assertThat(response.statusCode()).isEqualTo(400);
   assertThat(response.body()).contains("invalid_import","Import data is invalid");
  }
 }
}
