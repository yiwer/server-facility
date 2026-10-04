package com.example.api;

import com.example.api.bench.ImportController;
import workflow.fixture.CooperativeSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.web.multipart.MultipartFile;
import static org.assertj.core.api.Assertions.*;

class WorkflowImportInterruptionTest {
 @TempDir Path staging;
 @Timeout(15)
 @ParameterizedTest @ValueSource(booleans={false,true})
 void interruptsARealPartialWriteAndReusesTheImportOperation(boolean virtual) throws Exception {
  Files.createDirectories(staging.resolve("unrelated/nested"));
  Files.writeString(staging.resolve("unrelated/nested/sentinel"),"unchanged 🌱");
  var baseline=tree(staging); var operation=new ImportController(staging);
  byte[] prefix="name,quantity\npartial,".getBytes(StandardCharsets.UTF_8);
  var source=new CooperativeSource(prefix);
  MultipartFile file=new MultipartFile(){
   public String getName(){return "file";} public String getOriginalFilename(){return "partial.csv";}
   public String getContentType(){return "text/csv";} public boolean isEmpty(){return false;}
   public long getSize(){return 64;} public byte[] getBytes(){throw new AssertionError("stream required");}
   public InputStream getInputStream(){return source;} public void transferTo(File destination){throw new AssertionError("stream required");}
  };
  var request=request(file); var status=new AtomicInteger();var interrupted=new AtomicBoolean();var failure=new AtomicReference<Throwable>();
  Runnable action=()->{try{status.set(operation.importCsv(request).getStatusCode().value());}catch(Throwable e){failure.set(e);}finally{interrupted.set(Thread.currentThread().isInterrupted());}};
  Thread worker=virtual?Thread.ofVirtual().start(action):Thread.ofPlatform().start(action);
  try {
   assertThat(source.awaitBlocked(3,TimeUnit.SECONDS)).isTrue();assertThat(worker.isAlive()).isTrue();assertThat(status.get()).isZero();
   var partial=tree(staging);baseline.forEach((name,value)->assertThat(partial).containsEntry(name,value));
   try(var files=Files.walk(staging)){
    var added=files.filter(Files::isRegularFile).filter(path->!baseline.containsKey(staging.relativize(path).toString())).toList();
    assertThat(added).hasSize(1);assertThat(added.getFirst().getParent()).isNotEqualTo(staging);
    assertThat(Files.readAllBytes(added.getFirst())).containsExactly(prefix);
   }
   worker.interrupt();worker.join(3000);
   assertThat(worker.isAlive()).isFalse();assertThat(failure.get()).isNull();assertThat(status.get()).isEqualTo(400);
   assertThat(interrupted.get()).isTrue();assertThat(source.isClosed()).isTrue();assertThat(tree(staging)).isEqualTo(baseline);
   var healthy=operation.importCsv(request(new MockMultipartFile("file","good.csv","text/csv","name,quantity\nfirst,2\nsecond,3\n".getBytes(StandardCharsets.UTF_8))));
   assertThat(healthy.getStatusCode().value()).isEqualTo(200);
   var summary=(ImportController.ImportSummary)healthy.getBody();assertThat(summary.rows()).isEqualTo(2);assertThat(summary.totalQuantity()).isEqualTo(5);
   assertThat(tree(staging)).isEqualTo(baseline);
  } finally {source.close();worker.interrupt();worker.join(3000);assertThat(worker.isAlive()).isFalse();}
 }
 static MockMultipartHttpServletRequest request(MultipartFile file){
  var request=new MockMultipartHttpServletRequest();request.addFile(file);
  request.addPart(new MockPart("file",file.getOriginalFilename(),new byte[0]));return request;
 }
 static Map<String,String> tree(Path root)throws IOException{
  var state=new TreeMap<String,String>();try(var paths=Files.walk(root)){for(var path:paths.toList())state.put(root.relativize(path).toString(),Files.isDirectory(path)?"directory":Base64.getEncoder().encodeToString(Files.readAllBytes(path)));}return state;
 }
}
