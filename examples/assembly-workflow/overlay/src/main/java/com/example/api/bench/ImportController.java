package com.example.api.bench;

import cn.code91.facility.csv.CsvDialect;
import cn.code91.facility.csv.CsvLimits;
import cn.code91.facility.csv.CsvUtil;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.web.upload.SafeUpload;
import java.io.IOException;
import jakarta.servlet.ServletException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
public final class ImportController {
    private final Path staging;

    public ImportController(@Value("${bench.staging-directory}") Path staging) throws IOException {
        this.staging = Files.createDirectories(staging.toAbsolutePath().normalize());
    }

    @PostMapping(value = "/api/bench/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCsv(MultipartHttpServletRequest request) throws IOException, ServletException {
        var files = request.getFiles("file");
        if (files.size() != 1 || request.getParts().stream().filter(part -> part.getName().equals("file")).count() != 1) return invalid();
        Path directory = Files.createTempDirectory(staging, "import-");
        Path saved = null;
        try {
            var upload = SafeUpload.saveFile(files.getFirst(), directory, 4096, null);
            if (upload.isErr()) {
                return upload.getErr().isErrorType(FacilityErrorType.FILE_SIZE_EXCEEDED)
                        ? problem(HttpStatus.PAYLOAD_TOO_LARGE, "import_too_large", "Import exceeds the byte limit") : invalid();
            }
            saved = upload.get();
            // The CSV row budget includes the header; field limits use UTF-16 units.
            // Business names below use Unicode code points, so 40 supplementary characters need 80 units.
            var parsed = CsvUtil.readAll(saved, CsvDialect.STRICT, new CsvLimits(4096, 33, 2, 80));
            if (parsed.isErr()) return invalid();
            var records = parsed.get();
            if (records.size() < 2 || !records.getFirst().equals(List.of("name", "quantity"))) return invalid();
            var items = new ArrayList<ImportItem>();
            for (var row : records.subList(1, records.size())) {
                if (row.size() != 2) return invalid();
                String name = row.get(0), quantityText = row.get(1);
                if (name.isBlank() || name.codePointCount(0, name.length()) > 40) return invalid();
                final int quantity;
                try { quantity = Integer.parseInt(quantityText); }
                catch (NumberFormatException invalid) { return invalid(); }
                if (quantity < 1 || quantity > 1000) return invalid();
                items.add(new ImportItem(name, quantity));
            }
            return ResponseEntity.ok(new ImportSummary(items.size(), items.stream().mapToInt(ImportItem::quantity).sum(), List.copyOf(items)));
        } finally {
            try { if (saved != null) Files.deleteIfExists(saved); }
            finally { Files.deleteIfExists(directory); }
        }
    }

    private ResponseEntity<ProblemDetail> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "invalid_import", "Import data is invalid");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    public record ImportItem(String name, int quantity) {}
    public record ImportSummary(int rows, int totalQuantity, List<ImportItem> items) {}
}
