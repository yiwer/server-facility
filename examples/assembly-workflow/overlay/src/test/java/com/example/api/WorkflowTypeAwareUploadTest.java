package com.example.api;
import cn.code91.facility.web.upload.SafeUpload;
import cn.code91.facility.csv.*;
import cn.code91.facility.error.FacilityErrorType;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
/** Separate type-aware public pipeline; HTTP import intentionally uses size plus strict CSV admission. */
class WorkflowTypeAwareUploadTest {
 @TempDir Path staging;
 @Test void preservesSingleSourceCsvBytesAcrossTypeProbeAndParseAndReusesAfterRefusals() throws Exception {
  byte[] csv="name,quantity\nfirst,2\nsecond,3\n".getBytes(StandardCharsets.UTF_8);
  for(var bad:List.of(new MockMultipartFile("file","fake.csv","text/csv",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0}),
       new MockMultipartFile("file","large.csv","text/csv","a".repeat(4097).getBytes(StandardCharsets.UTF_8)))) {
   var refused=SafeUpload.saveFile(bad,staging,4096,Set.of("text/plain","text/csv"));
   assertThat(refused.isErr()).isTrue();
   assertThat(refused.getErr().getErrorType()).isIn(FacilityErrorType.FILE_TYPE_NOT_SUPPORTED,FacilityErrorType.FILE_SIZE_EXCEEDED);
   try(var files=Files.list(staging)){assertThat(files).isEmpty();}
  }
  var opens=new AtomicInteger();var closes=new AtomicInteger();
  var file=new MockMultipartFile("file","misleading.png","image/png",csv){
   @Override public InputStream getInputStream()throws IOException {
    if(opens.incrementAndGet()!=1)throw new IOException("single source");
    return new FilterInputStream(new ByteArrayInputStream(csv)){
     @Override public boolean markSupported(){return false;}
     @Override public void close()throws IOException{closes.incrementAndGet();super.close();}
    };
   }
  };
  var saved=SafeUpload.saveFile(file,staging,4096,Set.of("text/plain","text/csv"));assertThat(saved.isOk()).isTrue();
  try {
   assertThat(Files.readAllBytes(saved.get())).containsExactly(csv);
   assertThat(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(saved.get())))
       .containsExactly(MessageDigest.getInstance("SHA-256").digest(csv));
   var parsed=CsvUtil.readAll(saved.get(),CsvDialect.STRICT,new CsvLimits(4096,33,2,80));
   assertThat(parsed.isOk()).isTrue();assertThat(parsed.get()).containsExactly(List.of("name","quantity"),List.of("first","2"),List.of("second","3"));
   assertThat(opens).hasValue(1);assertThat(closes).hasValue(1);
  }finally{Files.delete(saved.get());}
  try(var files=Files.list(staging)){assertThat(files).isEmpty();}
 }
}
