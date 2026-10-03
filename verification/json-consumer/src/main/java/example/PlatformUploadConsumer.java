package example;

import cn.code91.facility.web.upload.SafeUpload;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Installed ordinary jar, real Spring MultipartFile API, and graphs with/without optional Tika. */
public final class PlatformUploadConsumer {
    public static void main(String[] args) throws Exception {
        boolean tika = args[0].equals("tika");
        Path root = Files.createDirectories(Path.of(args[1]));
        try { Class.forName("org.apache.tika.Tika"); require(tika, "Tika must be genuinely absent"); }
        catch (ClassNotFoundException missing) { require(!tika, "selected detector is absent"); }
        require(SafeUpload.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"), "ordinary library jar");
        for (String absent : List.of("org.springframework.mock.web.MockMultipartFile", "org.apache.poi.ss.usermodel.Workbook")) {
            try { Class.forName(absent); throw new AssertionError("optional/test graph leaked: " + absent); }
            catch (ClassNotFoundException expected) { }
        }
        byte[] expected = "hello".getBytes(StandardCharsets.UTF_8);
        MultipartFile source = new ByteFile(expected);
        Path plain = SafeUpload.saveFile(source, root, 5, null).get();
        require(Arrays.equals(Files.readAllBytes(plain), expected), "raw save does not require Tika");
        Files.delete(plain);
        require(SafeUpload.saveFile(source, root, 4, null).isErr(), "actual bytes exceed explicit budget despite false metadata");
        if (tika) {
            Path typed = SafeUpload.saveFile(source, root, 5, Set.of("text/plain")).get();
            require(Arrays.equals(Files.readAllBytes(typed), expected), "byte-derived MIME save preserves bytes");
            require(typed.getFileName().toString().matches("[0-9a-f-]{36}\\.upload"), "server storage key, not client display name");
            Files.delete(typed);
            require(SafeUpload.saveFile(source, root, 5, Set.of("application/pdf")).isErr(), "false filename/content-type cannot satisfy content policy");
        } else {
            try {
                SafeUpload.saveFile(source, root, 5, Set.of("text/plain"));
                throw new AssertionError("missing detector accepted a required content policy");
            } catch (NoClassDefFoundError expectedMissing) {
                require(expectedMissing.getMessage().contains("org/apache/tika"), "unrelated linkage failure: " + expectedMissing);
            }
        }
        Path after = SafeUpload.saveFile(source, root, 5, null).get();
        Files.delete(after);
        try (var paths = Files.list(root)) { require(paths.findAny().isEmpty(), "no own staging file remains"); }
        System.out.println("PLATFORM_UPLOAD_OK " + args[0] + " stageFiles=0 rawSaveWorks=true");
    }

    private record ByteFile(byte[] value) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "claimed.pdf"; }
        public String getContentType() { return "application/pdf"; }
        public boolean isEmpty() { return false; }
        public long getSize() { return 0; } // deliberately false metadata
        public byte[] getBytes() { return value.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(value); }
        public void transferTo(File destination) { throw new AssertionError("must use budgeted source stream"); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
