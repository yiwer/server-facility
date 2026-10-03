import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** JDK-only entry point. Run from the repository root: java verification/Verify.java all --fresh. */
class Verify {
    static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    static final Path ROOT = Path.of("").toAbsolutePath().normalize();
    static Path report;
    static Path repository;
    static Path wrapperHome;
    static final List<String> summary = new ArrayList<>();
    static int sequence;

    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 25) {
            throw new IllegalStateException("Verification requires JDK 25; set JAVA_HOME and PATH to JDK 25.");
        }
        var mode = args.length == 0 ? "all" : args[0];
        if (!Set.of("fast", "integration", "resources", "all", "prerequisites", "platform").contains(mode)) {
            throw new IllegalArgumentException("Use fast, integration, resources, all, prerequisites or platform; optional --fresh.");
        }
        if (!Files.isRegularFile(ROOT.resolve(".mvn/wrapper/maven-wrapper.properties"))) {
            throw new IllegalStateException("Run verification/Verify.java from the repository root; checked-in Wrapper is required.");
        }
        var stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
        report = Files.createDirectories(ROOT.resolve(".verification-results/" + stamp + "-" + mode));
        // The Wrapper's PowerShell extraction still encounters legacy Windows path limits.
        // Keep its cache short even when the descriptive evidence directory name is long.
        wrapperHome = ROOT.resolve(".verification-results/w-" + UUID.randomUUID().toString().substring(0, 8));
        repository = Arrays.asList(args).contains("--fresh") ? report.resolve("repository")
                : ROOT.resolve(".verification-results/repository");
        Files.createDirectories(repository);
        summary.add("mode=" + mode + " time=" + Instant.now());
        summary.add("java=" + System.getProperty("java.runtime.version") + " vendor=" + System.getProperty("java.vendor"));
        summary.add("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " arch=" + System.getProperty("os.arch") + " timezone=" + ZoneId.systemDefault() + " locale=" + Locale.getDefault());
        summary.add("repository=" + repository + " fresh=" + Arrays.asList(args).contains("--fresh"));
        summary.add("wrapper-home=" + wrapperHome);
        try {
            run(ROOT, Map.of(), "revision", List.of("git", "rev-parse", "HEAD"), 30, null);
            run(ROOT, Map.of(), "working-tree", List.of("git", "status", "--short"), 30, null);
            maven(ROOT, "toolchain", "--version");
            if (mode.equals("platform")) {
                platformProbe();
                summary.add("scope=toolchain-only; library compilation, runtime and full quality gates are NOT verified by this mode");
            } else if (mode.equals("prerequisites")) {
                prerequisites();
            } else {
                maven(ROOT, "library", "clean", mode.equals("fast") ? "verify" : "install");
                archiveQuality();
                maven(ROOT, "effective-pom", "help:effective-pom", "-Doutput=" + report.resolve("effective-pom.xml"));
                maven(ROOT, "dependency-tree", "dependency:tree", "-DoutputFile=" + report.resolve("dependency-tree.txt"));
                if (!mode.equals("fast")) {
                    consumerBuild();
                    consumer("configured");
                    consumer("override");
                    consumer("invalid");
                    coreConsumer();
                    cryptoConsumer();
                    ioConsumer();
                    csvConsumer();
                    rateLimitConsumer();
                    claimConsumer();
                    jsonConsumer();
                    platformConsumers();
                    partnerConsumer();
                    securedTemplate();
                }
                if (mode.equals("resources") || mode.equals("all")) {
                    // Each application gets a distinct bounded JVM and must close naturally within 45 seconds.
                    for (int i = 1; i <= 5; i++) consumer("configured");
                    summary.add("resources=5 repeated application startup/use/close cycles; -Xmx256m; 45s deadline per JVM");
                }
                if (mode.equals("integration") || mode.equals("all")) prerequisites();
            }
            summary.add("RESULT=PASS");
        } catch (Exception | AssertionError failure) {
            summary.add("RESULT=FAIL " + failure);
            throw failure;
        } finally {
            Files.write(report.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
            System.out.println("Evidence: " + report);
        }
    }

    static void maven(Path directory, String name, String... goals) throws Exception {
        maven(directory, name, null, List.of(goals));
    }

    static void maven(Path directory, String name, String expectedFailure, List<String> goals) throws Exception {
        var command = wrapper(directory);
        command.addAll(List.of("-B", "-ntp", "-C", "-s", ROOT.resolve("verification/settings.xml").toString(),
                "-gs", ROOT.resolve("verification/settings.xml").toString(), "-Dmaven.repo.local=" + repository));
        command.addAll(goals);
        run(directory, Map.of("MAVEN_USER_HOME", wrapperHome.toString()), name, command, 1200, expectedFailure);
    }

    static void platformProbe() throws Exception {
        Path probe = ROOT.resolve("verification/platform-probe");
        Path inputs = Files.createDirectories(report.resolve("platform-inputs"));
        Files.copy(probe.resolve("pom.xml"), inputs.resolve("pom.xml"));
        Files.copy(ROOT.resolve("pom.xml"), inputs.resolve("library-pom.xml"));
        Files.copy(ROOT.resolve("verification/json-consumer/pom.xml"), inputs.resolve("json-consumer-pom.xml"));
        copyDirectory(probe.resolve("src"), inputs.resolve("src"));
        try (var files = Files.walk(inputs)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                summary.add("sha256 platform-inputs/" + inputs.relativize(file) + "=" + HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
        }
        maven(probe, "platform-positive", "clean", "verify");
        platformReports(probe, "positive", null);
        copyDirectory(probe.resolve("target/site/jacoco"), report.resolve("platform-positive/jacoco"));
        Files.copy(probe.resolve("target/classes/META-INF/spring-configuration-metadata.json"),
                report.resolve("platform-positive/spring-configuration-metadata.json"));
        Path jar = probe.resolve("target/platform-probe-1.0-SNAPSHOT.jar");
        Files.copy(jar, report.resolve("platform-positive/platform-probe.jar"));
        summary.add("sha256 platform-probe.jar=" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        var coverage = factory.newDocumentBuilder().parse(probe.resolve("target/site/jacoco/jacoco.xml").toFile());
        boolean instrumented = false;
        var classes = coverage.getElementsByTagName("class");
        for (int i = 0; i < classes.getLength(); i++) {
            var type = (Element) classes.item(i);
            if (type.getAttribute("name").equals("probe/model/ProbeProperties")) {
                var counters = type.getElementsByTagName("counter");
                for (int j = 0; j < counters.getLength(); j++) {
                    var counter = (Element) counters.item(j);
                    instrumented |= counter.getAttribute("type").equals("INSTRUCTION")
                            && Long.parseLong(counter.getAttribute("covered")) > 0;
                }
            }
        }
        if (!instrumented) throw new AssertionError("JaCoCo did not instrument and observe the Java 25 probe class");
        maven(probe, "platform-effective-pom", "help:effective-pom", "-Doutput=" + report.resolve("platform-positive/effective-pom.xml"));
        maven(probe, "platform-dependency-tree", "dependency:tree", "-DoutputFile=" + report.resolve("platform-positive/dependency-tree.txt"));
        maven(probe, "platform-jupiter-negative", "intentional Jupiter discovery control",
                List.of("test", "-Dprobe.fail.jupiter=true"));
        platformReports(probe, "jupiter-negative", "targetPlatformAndJupiterAreActuallyLoaded");
        maven(probe, "platform-archunit-negative", "IntentionallyWrong",
                List.of("test", "-Dprobe.fail.archunit=true"));
        platformReports(probe, "archunit-negative", "engine_negative_control");
        // Resolve the real library model even while ticket 23 owns Jackson source migration.
        // These goals do not compile the library and cannot stand in for 'all'.
        maven(ROOT, "target-effective-pom", "help:effective-pom", "-Doutput=" + report.resolve("effective-pom.xml"));
        maven(ROOT, "target-dependency-tree", "dependency:tree", "-DoutputFile=" + report.resolve("dependency-tree.txt"));
        maven(ROOT, "target-dependency-resolution", "dependency:resolve");
        summary.add("platform=positive Jupiter+ArchUnit discovery; each engine separately rejects its negative control; processors/classfile69.0/JaCoCo verified; target dependencies resolved");
    }

    static void platformReports(Path probe, String scenario, String expectedFailedTest) throws Exception {
        Path reports = probe.resolve("target/surefire-reports");
        copyDirectory(reports, report.resolve("platform-" + scenario + "/surefire-reports"));
        var expected = Set.of("targetPlatformAndJupiterAreActuallyLoaded", "processorsAndClassfileWorkOnJava25WithoutPreview",
                "java25_record_is_imported", "engine_negative_control", "actualTechnologyTypesResolveFromTheirTargetModules");
        var discovered = new HashSet<String>();
        var failed = new HashSet<String>();
        int count = 0;
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var files = Files.list(reports)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml")).toList()) {
                var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                if (!suite.getAttribute("errors").equals("0") || !suite.getAttribute("skipped").equals("0")) {
                    throw new AssertionError("Unexpected errors/skips in platform " + scenario + ": " + file);
                }
                var cases = suite.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    count++;
                    var test = (Element) cases.item(i);
                    String name = test.getAttribute("name").replace("()", "");
                    discovered.add(name);
                    if (test.getElementsByTagName("failure").getLength() != 0) failed.add(name);
                }
            }
        }
        Set<String> expectedFailures = expectedFailedTest == null ? Set.of() : Set.of(expectedFailedTest);
        if (count != expected.size() || !discovered.equals(expected) || !failed.equals(expectedFailures)) {
            throw new AssertionError("Platform " + scenario + " discovery=" + discovered + " count=" + count + " failed=" + failed);
        }
        summary.add("platform-" + scenario + " tests=" + count + " intentional-failures=" + failed);
    }

    static ArrayList<String> wrapper(Path directory) {
        // Execute the root wrapper while Maven's working directory may be the independent consumer.
        var wrapper = directory.resolve(WINDOWS ? "mvnw.cmd" : "mvnw");
        if (!Files.exists(wrapper)) wrapper = ROOT.resolve(WINDOWS ? "mvnw.cmd" : "mvnw");
        return new ArrayList<>(WINDOWS ? List.of("cmd.exe", "/d", "/c", wrapper.toString())
                : List.of("sh", wrapper.toString()));
    }

    static void consumerBuild() throws Exception {
        var consumer = ROOT.resolve("verification/consumer");
        maven(consumer, "consumer-build", "clean", "compile", "dependency:build-classpath",
                "-Dmdep.outputFile=" + consumer.resolve("target/classpath.txt"));
    }

    static void securedTemplate() throws Exception {
        Path application = Files.createTempDirectory("facility-template-").resolve("secured api-示例");
        Path inputs = report.resolve("template-inputs");
        Path evidence = Files.createDirectories(report.resolve("template"));
        Path copier = ROOT.resolve("templates/Instantiate.java");
        Path client = ROOT.resolve("verification/template-consumer/TemplateConsumer.java");
        Files.copy(copier, evidence.resolve("Instantiate.java"));
        Files.copy(client, evidence.resolve("TemplateConsumer.java"));
        run(ROOT, Map.of(), "template-instantiate", List.of(java(), copier.toString(),
                ROOT.resolve("templates/secured-api").toString(), application.toString()), 45, null);
        run(ROOT, Map.of(), "template-refuse-overwrite", List.of(java(), copier.toString(),
                ROOT.resolve("templates/secured-api").toString(), application.toString()), 45,
                "Destination must be new and outside the template directory");
        copyDirectory(application, inputs);
        try (var files = Files.walk(inputs)) {
            for (Path input : files.filter(Files::isRegularFile).sorted().toList()) {
                summary.add("sha256 template-inputs/" + inputs.relativize(input) + "=" + HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input))));
            }
        }
        summary.add("template-independent-directory=" + application);
        try {
            maven(application, "template-build", "clean", "verify");
        } finally {
            if (Files.isDirectory(application.resolve("target/surefire-reports")))
                copyDirectory(application.resolve("target/surefire-reports"), evidence.resolve("surefire-reports"));
            if (Files.isDirectory(application.resolve("target/site/jacoco")))
                copyDirectory(application.resolve("target/site/jacoco"), evidence.resolve("jacoco"));
            for (boolean rollback : List.of(false, true)) {
                Path log = application.resolve("target/decorator-failure-" + rollback + ".log");
                if (Files.isRegularFile(log)) Files.copy(log, evidence.resolve(log.getFileName()));
            }
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        long tests = 0;
        var discovered = new HashSet<String>();
        try (var files = Files.list(evidence.resolve("surefire-reports"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml")).toList()) {
                var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                tests += Long.parseLong(suite.getAttribute("tests"));
                discovered.add(suite.getAttribute("name"));
                for (String outcome : List.of("failures", "errors", "skipped")) {
                    if (Long.parseLong(suite.getAttribute(outcome)) != 0) throw new AssertionError("Template " + outcome + ": " + file);
                }
            }
        }
        if (!discovered.containsAll(Set.of("com.example.api.AuthenticationHttpTest", "com.example.api.FailureLifecycleHttpTest",
                "com.example.api.JwkLifecycleHttpTest", "com.example.api.ConfigurationHttpTest", "com.example.api.DecoratorFailureTest",
                "com.example.api.ExecutorOwnershipTest", "com.example.api.BusinessBoundaryTest")))
            throw new AssertionError("Missing template contract tests: " + discovered);
        maven(application, "template-model", "help:effective-pom", "-Doutput=" + evidence.resolve("effective-pom.xml"));
        maven(application, "template-dependencies", "dependency:tree", "-DoutputFile=" + evidence.resolve("dependency-tree.txt"),
                "dependency:build-classpath", "-Dmdep.outputFile=" + evidence.resolve("classpath.txt"));
        installedClasspath(application, evidence.resolve("classpath.txt"));
        Path jar = application.resolve("target/secured-api-1.0.0-SNAPSHOT.jar");
        try (var archive = new java.util.zip.ZipFile(jar.toFile())) {
            var library = archive.getEntry("BOOT-INF/lib/server-facility-0.1.0-SNAPSHOT.jar");
            if (library == null) throw new AssertionError("Template did not package the ordinary library jar");
            try (var packaged = archive.getInputStream(library)) {
                if (!Arrays.equals(packaged.readAllBytes(), Files.readAllBytes(ROOT.resolve("target/server-facility-0.1.0-SNAPSHOT.jar"))))
                    throw new AssertionError("Packaged template consumed another build's library jar");
            }
            if (archive.stream().anyMatch(entry -> entry.getName().contains("LocalIssuer") || entry.getName().contains("TestIssuer")))
                throw new AssertionError("Development/test signing fixtures leaked into production jar");
        }
        Files.copy(jar, Files.createDirectories(evidence.resolve("artifacts")).resolve(jar.getFileName()));
        Path log = run(application, Map.of(), "template-packaged-http", List.of(java(), "-Xmx96m", client.toString(),
                application.toString(), evidence.toString()), 180, null);
        if (!Files.readString(log).contains("PACKAGED_TEMPLATE_PASS")) throw new AssertionError("Missing packaged template result");
        Path withoutCoverage = application.getParent().resolve("without-coverage");
        run(ROOT, Map.of(), "template-coverage-probe-instantiate", List.of(java(), copier.toString(),
                ROOT.resolve("templates/secured-api").toString(), withoutCoverage.toString()), 45, null);
        maven(withoutCoverage, "template-missing-coverage-rejected",
                "Executed coverage data and report are required; missing coverage is not success",
                List.of("clean", "verify", "-DskipTests"));
        summary.add("template-coverage-negative=clean independent copy without test execution rejected by required coverage gate");
        summary.add("template=tests " + tests + " failures=0 errors=0 skipped=0; fresh directory outside checkout; independent Wrapper; actual packaged HTTP platform/virtual");
        summary.add("sha256 secured-api.jar=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
    }

    static void consumer(String scenario) throws Exception {
        var consumer = ROOT.resolve("verification/consumer");
        String dependencies = Files.readString(consumer.resolve("target/classpath.txt")).trim();
        for (String entry : dependencies.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.endsWith(".jar") || !Path.of(entry).toAbsolutePath().startsWith(repository)) {
                throw new AssertionError("Consumer dependency must be a repository jar, never source/test output: " + entry);
            }
        }
        var classpath = consumer.resolve("target/classes") + File.pathSeparator + dependencies;
        Path log = run(ROOT, Map.of(), "consumer-" + scenario, List.of(java(), "-Xmx256m", "-Dfile.encoding=UTF-8",
                "-cp", classpath, "example.Consumer", scenario), 45, null);
        if (!Files.readString(log).contains("CONSUMER_OK " + scenario)) {
            throw new AssertionError("Consumer did not verify scenario " + scenario + ": " + log);
        }
    }

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();
    }

    static void coreConsumer() throws Exception {
        Path jar = repository.resolve("cn/code91/server-facility/0.1.0-SNAPSHOT/server-facility-0.1.0-SNAPSHOT.jar");
        Path source = ROOT.resolve("verification/core-consumer/CoreConsumer.java");
        Path classes = Files.createDirectories(report.resolve("core-consumer/classes"));
        Files.copy(source, report.resolve("core-consumer/CoreConsumer.java"));
        String javac = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac").toString();
        run(ROOT, Map.of(), "core-consumer-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8",
                "-cp", jar.toString(), "-d", classes.toString(), source.toString()), 45, null);
        Path log = run(ROOT, Map.of(), "core-consumer", List.of(java(), "-Xmx64m", "-Dfile.encoding=UTF-8",
                "-cp", classes + File.pathSeparator + jar, "CoreConsumer"), 45, null);
        if (!Files.readString(log).contains("CORE_CONSUMER_PASS seed=180041 iterations=512 framework=absent")) {
            throw new AssertionError("Core consumer did not complete: " + log);
        }
        summary.add("core-consumer=ordinary jar only; no framework/annotation/third-party runtime; domain business and compatibility; seed180041/512; -Xmx64m/45s");
    }

    static void cryptoConsumer() throws Exception {
        Path jar = repository.resolve("cn/code91/server-facility/0.1.0-SNAPSHOT/server-facility-0.1.0-SNAPSHOT.jar");
        Path source = ROOT.resolve("verification/crypto-consumer/CryptoConsumer.java");
        Path classes = Files.createDirectories(report.resolve("crypto-consumer/classes"));
        Files.copy(source, report.resolve("crypto-consumer/CryptoConsumer.java"));
        String javac = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac").toString();
        run(ROOT, Map.of(), "crypto-consumer-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8",
                "-cp", jar.toString(), "-d", classes.toString(), source.toString()), 45, null);
        Path log = run(ROOT, Map.of(), "crypto-consumer", List.of(java(), "-Xmx64m", "-Dfile.encoding=UTF-8",
                "-cp", classes + File.pathSeparator + jar, "CryptoConsumer"), 45, null);
        if (!Files.readString(log).contains("CRYPTO_CONSUMER_PASS legacy=210000 max-bytes=1048576 rounds=64 workers=4 framework=absent")) {
            throw new AssertionError("Crypto consumer did not complete: " + log);
        }
        summary.add("crypto-consumer=ordinary jar only; persisted legacy receipt; explicit input budgets; 64x1MiB sequential and 4x16x256KiB concurrent; -Xmx64m/45s");
    }

    static void ioConsumer() throws Exception {
        Path jar = repository.resolve("cn/code91/server-facility/0.1.0-SNAPSHOT/server-facility-0.1.0-SNAPSHOT.jar");
        Path source = ROOT.resolve("verification/io-consumer/IoConsumer.java");
        Path classes = Files.createDirectories(report.resolve("io-consumer/classes"));
        Files.copy(source, report.resolve("io-consumer/IoConsumer.java"));
        String javac = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac").toString();
        run(ROOT, Map.of(), "io-consumer-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8",
                "-cp", jar.toString(), "-d", classes.toString(), source.toString()), 45, null);
        Path log = run(ROOT, Map.of(), "io-consumer", List.of(java(), "-Xmx64m", "-Dfile.encoding=UTF-8",
                "-cp", classes + File.pathSeparator + jar, "IoConsumer", report.resolve("io-consumer/work").toString()), 60, null);
        if (!Files.readString(log).contains("IO_CONSUMER_PASS seed=140037 archives=64 max-input-mib=128 failures=200 framework=absent")) {
            throw new AssertionError("IO consumer did not complete: " + log);
        }
        summary.add("io-consumer=ordinary jar only; independent JDK ZipFile; seed140037/64 archives; 32/128 MiB source under -Xmx64m; 200 failures; 60s deadline");
    }

    static void csvConsumer() throws Exception {
        Path consumer = ROOT.resolve("verification/consumer");
        Files.copy(consumer.resolve("src/main/java/example/CsvConsumer.java"), report.resolve("CsvConsumer.java"));
        Files.copy(consumer.resolve("pom.xml"), report.resolve("csv-consumer-pom.xml"));
        maven(consumer, "csv-consumer-dependencies", "dependency:tree",
                "-DoutputFile=" + report.resolve("csv-consumer-dependency-tree.txt"));
        String dependencies = Files.readString(consumer.resolve("target/classpath.txt")).trim();
        Files.writeString(report.resolve("csv-consumer-classpath.txt"), dependencies);
        Path log = run(ROOT, Map.of(), "csv-consumer", List.of(java(), "-Xmx64m", "-Dfile.encoding=UTF-8",
                "-cp", consumer.resolve("target/classes") + File.pathSeparator + dependencies,
                "example.CsvConsumer"), 45, null);
        if (!Files.readString(log).contains("CSV_CONSUMER_PASS rows=200000 optional-tika=absent optional-poi=absent")) {
            throw new AssertionError("CSV consumer did not complete: " + log);
        }
        summary.add("csv-consumer=ordinary jar with required transitive dependencies; Tika/POI absent; literal golden/dialects/budgets/formula policy; 200000 streamed rows; -Xmx64m/45s");
    }

    static void rateLimitConsumer() throws Exception {
        Path jar = repository.resolve("cn/code91/server-facility/0.1.0-SNAPSHOT/server-facility-0.1.0-SNAPSHOT.jar");
        Path source = ROOT.resolve("verification/rate-limit-consumer/RateLimitConsumer.java");
        Path classes = Files.createDirectories(report.resolve("rate-limit-consumer/classes"));
        Files.copy(source, report.resolve("rate-limit-consumer/RateLimitConsumer.java"));
        String javac = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac").toString();
        run(ROOT, Map.of(), "rate-limit-consumer-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8",
                "-cp", jar.toString(), "-d", classes.toString(), source.toString()), 45, null);
        Path log = run(ROOT, Map.of(), "rate-limit-consumer", List.of(java(), "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                "-cp", classes + File.pathSeparator + jar, "RateLimitConsumer"), 45, null);
        if (!Files.readString(log).contains("RATE_LIMIT_CONSUMER_PASS slots=1024 churn=32768 workers=16 exact-long=true framework=absent")) {
            throw new AssertionError("Rate-limit consumer did not complete: " + log);
        }
        summary.add("rate-limit-consumer=ordinary jar only; no framework runtime; 1024 slots/512-char keys/32768 churn+illegal-cost attempts/16 workers; -Xmx64m/2 processors/45s");
    }

    static void partnerConsumer() throws Exception {
        Path owned = Files.createTempDirectory("facility-partner-").toRealPath();
        Path application = owned.resolve("partner app-示例");
        Path source = ROOT.resolve("examples/partner-aggregation");
        Path evidence = Files.createDirectories(report.resolve("partner"));
        Path inputs = Files.createDirectories(report.resolve("partner-inputs"));
        copyDirectory(source.resolve("src"), application.resolve("src"));
        Files.copy(source.resolve("pom.xml"), application.resolve("pom.xml"));
        copyDirectory(ROOT.resolve(".mvn"), application.resolve(".mvn"));
        for (String name : List.of("mvnw", "mvnw.cmd")) Files.copy(ROOT.resolve(name), application.resolve(name));
        copyDirectory(application, inputs);
        summary.add("partner-independent-copy=" + application);
        maven(application, "partner-build", "clean", "verify", "dependency:build-classpath", "-DincludeScope=runtime",
                "-Dmdep.outputFile=" + evidence.resolve("runtime-classpath.txt"));
        maven(application, "partner-model", "help:effective-pom", "dependency:tree", "-DincludeScope=runtime",
                "-Doutput=" + evidence.resolve("effective-pom.xml"), "-DoutputFile=" + evidence.resolve("dependency-tree.txt"));
        copyDirectory(application.resolve("target/surefire-reports"), evidence.resolve("surefire-reports"));
        copyDirectory(application.resolve("target/site/jacoco"), evidence.resolve("jacoco"));
        if (!Files.isRegularFile(evidence.resolve("jacoco/jacoco.xml"))) throw new AssertionError("Partner coverage missing");
        Path jar = evidence.resolve("partner-aggregation.jar");
        Files.copy(application.resolve("target/partner-aggregation-1.0-SNAPSHOT.jar"), jar);
        summary.add("sha256 partner-aggregation.jar=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        installedClasspath(application, evidence.resolve("runtime-classpath.txt")); // Validates exact installed library identity.
        String dependencies = Files.readString(evidence.resolve("runtime-classpath.txt")).trim();
        Path probe = ROOT.resolve("verification/partner-consumer/PartnerConsumer.java");
        Files.copy(probe, evidence.resolve("PartnerConsumer.java"));
        Path classes = Files.createDirectories(evidence.resolve("classes"));
        String classpath = jar + File.pathSeparator + dependencies;
        String javac = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac").toString();
        run(ROOT, Map.of(), "partner-consumer-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8", "-cp", classpath,
                "-d", classes.toString(), probe.toString()), 45, null);
        Path log = run(ROOT, Map.of(), "partner-consumer", List.of(java(), "-Xmx128m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                "-Djdk.net.unixdomain.tmpdir=" + classes, "-cp", classes + File.pathSeparator + classpath, "PartnerConsumer"), 90, null);
        if (!Files.readString(log).contains("PARTNER_CONSUMER_PASS cycles=5 rejected=200 wire=205")) throw new AssertionError("Partner resource consumer did not finish");
        // A new clean copy must not mistake skipped/missing instrumentation for successful coverage.
        Path negative = owned.resolve("negative");
        copyDirectory(inputs, negative);
        maven(negative, "partner-missing-coverage-negative", "Executed coverage data and report are required", List.of("clean", "verify", "-DskipTests"));
        summary.add("partner=independent Unicode/space copy; clean quality gate and absent-coverage negative; ordinary application+library jars; runtime-only graph; real HTTP/tracing/errors/deadlines; 5 lifecycle/200 bounded-tail failures; -Xmx128m/90s");
        // Only remove the exact directory created above, after evidence and artifacts have been archived.
        if (!owned.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                || !owned.getFileName().toString().startsWith("facility-partner-")) throw new AssertionError("Unexpected temporary application root");
        try (var paths = Files.walk(owned)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }

    static void claimConsumer() throws Exception {
        Path jar = repository.resolve("cn/code91/server-facility/0.1.0-SNAPSHOT/server-facility-0.1.0-SNAPSHOT.jar");
        Path inputs = ROOT.resolve("verification/claim-consumer");
        Path saved = report.resolve("claim-consumer/inputs");
        copyDirectory(inputs, saved);
        Path classes = Files.createDirectories(report.resolve("claim-consumer/classes"));
        Path legacy = Files.createDirectories(report.resolve("claim-consumer/legacy-classes"));
        String javac = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac").toString();
        run(ROOT, Map.of(), "claim-legacy-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8",
                "-cp", jar.toString(), "-d", legacy.toString(),
                saved.resolve("legacy-api/cn/code91/facility/idempotency/IdempotencyStore.java").toString(),
                saved.resolve("LegacyOnlyStore.java").toString()), 45, null);
        // Only the historical implementation enters runtime; the historical interface never shadows the new jar.
        Files.copy(legacy.resolve("LegacyOnlyStore.class"), classes.resolve("LegacyOnlyStore.class"));
        run(ROOT, Map.of(), "claim-consumer-compile", List.of(javac, "--release", "25", "-encoding", "UTF-8",
                "-cp", classes + File.pathSeparator + jar, "-d", classes.toString(), saved.resolve("ClaimConsumer.java").toString(),
                saved.resolve("ClaimFailureProbe.java").toString()), 45, null);
        Path log = run(ROOT, Map.of(), "claim-consumer", List.of(java(), "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                "-cp", classes + File.pathSeparator + jar, "ClaimConsumer"), 45, null);
        if (!Files.readString(log).contains("CLAIM_CONSUMER_PASS seed=110034 rounds=2048 slots=256 churn=32768 workers=16 close-rounds=128 legacy-binary=true framework=absent"))
            throw new AssertionError("Claim consumer did not complete: " + log);
        for (String mode : List.of("clone", "close-tables", "clock-error")) {
            Path faultLog = run(ROOT, Map.of(), "claim-failure-" + mode, List.of(java(), "-Xmx32m", "-XX:ActiveProcessorCount=2",
                    "-Dfile.encoding=UTF-8", "-cp", classes + File.pathSeparator + jar, "ClaimFailureProbe", mode), 45, null);
            if (!Files.readString(faultLog).contains("CLAIM_FAILURE_PROBE_PASS mode=" + mode))
                throw new AssertionError("Claim failure probe did not complete " + mode + ": " + faultLog);
        }
        summary.add("claim-consumer=ordinary jar; pre-expansion SPI binary; owner barrier; seed110034/2048; 256 slots/32768 churn/16 workers/128 closed reachable stores; -Xmx64m/2 processors/45s");
        summary.add("claim-failures=clone OOME with 20MiB input; host Clock Error preserved; 2048 reachable closed stores each formerly holding 4096 mixed entries; -Xmx32m/2 processors/45s per JVM");
    }

    static void jsonConsumer() throws Exception {
        var consumer = ROOT.resolve("verification/json-consumer");
        maven(consumer, "json-consumer-build", "clean", "compile", "dependency:build-classpath",
                "-Dmdep.outputFile=" + consumer.resolve("target/classpath.txt"));
        maven(consumer, "json-consumer-effective-pom", "help:effective-pom",
                "-Doutput=" + report.resolve("json-consumer-effective-pom.xml"));
        maven(consumer, "json-consumer-dependencies", "dependency:tree",
                "-DoutputFile=" + report.resolve("json-consumer-dependency-tree.txt"));
        copyDirectory(consumer.resolve("src/main/resources/golden"), report.resolve("json-golden"));
        Files.copy(consumer.resolve("pom.xml"), report.resolve("json-consumer-pom.xml"));
        Files.copy(consumer.resolve("src/main/java/example/JsonConsumer.java"), report.resolve("JsonConsumer.java"));
        Files.copy(consumer.resolve("src/main/java/example/PlatformWebConsumer.java"), report.resolve("PlatformWebConsumer.java"));
        Files.copy(consumer.resolve("src/main/java/example/PlatformUploadConsumer.java"), report.resolve("PlatformUploadConsumer.java"));
        try (var goldens = Files.list(report.resolve("json-golden"))) {
            for (Path golden : goldens.sorted().toList()) {
                summary.add("sha256 json-golden/" + golden.getFileName() + "=" + HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(golden))));
            }
        }
        String dependencies = Files.readString(consumer.resolve("target/classpath.txt")).trim();
        for (String entry : dependencies.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.endsWith(".jar") || !Path.of(entry).toAbsolutePath().startsWith(repository)) {
                throw new AssertionError("JSON consumer dependency is not an isolated repository jar: " + entry);
            }
        }
        var classpath = consumer.resolve("target/classes") + File.pathSeparator + dependencies;
        for (String access : List.of("constructed", "injected")) {
            Path log = run(ROOT, Map.of(), "json-consumer-" + access, List.of(java(), "-Xmx256m", "-Dfile.encoding=UTF-8",
                    "-cp", classpath, "example.JsonConsumer", access), 90, null);
            if (!Files.readString(log).contains("JSON_CONSUMER_OK " + access)) {
                throw new AssertionError("JSON consumer did not complete " + access + ": " + log);
            }
        }
        summary.add("json-consumer=ordinary jar; literal goldens; real HTTP; default/custom policy; two applications; close/rebuild; -Xmx256m; 90s per JVM");
        String installed = installedClasspath(consumer, consumer.resolve("target/classpath.txt"));
        for (String scenario : List.of("default", "user", "disabled")) {
            Path log = run(ROOT, Map.of(), "platform-web-" + scenario, List.of(java(), "-Xmx256m", "-Dfile.encoding=UTF-8",
                    "-cp", installed, "example.PlatformWebConsumer", scenario), 60, null);
            if (!Files.readString(log).contains("PLATFORM_WEB_OK " + scenario)) {
                throw new AssertionError("Web platform consumer did not complete " + scenario + ": " + log);
            }
        }
        summary.add("platform-web=default/user/disabled; servlet registrations and actual filter order; application/MVC mapper identity; repeatable 413; replay; ERROR dispatch; -Xmx256m; 60s per JVM");
        for (String graph : List.of("no-tika", "tika")) {
            Path evidence = Files.createDirectories(report.resolve("matrix/upload-" + graph));
            if (graph.equals("tika")) {
                maven(consumer, "upload-tika-build", "-Ptika", "clean", "compile", "dependency:build-classpath",
                        "-Dmdep.outputFile=" + evidence.resolve("classpath.txt"));
                maven(consumer, "upload-tika-model", "-Ptika", "help:effective-pom", "dependency:tree",
                        "-Doutput=" + evidence.resolve("effective-pom.xml"), "-DoutputFile=" + evidence.resolve("dependency-tree.txt"));
            } else {
                Files.copy(consumer.resolve("target/classpath.txt"), evidence.resolve("classpath.txt"));
                Files.copy(report.resolve("json-consumer-effective-pom.xml"), evidence.resolve("effective-pom.xml"));
                Files.copy(report.resolve("json-consumer-dependency-tree.txt"), evidence.resolve("dependency-tree.txt"));
            }
            String uploadClasspath = installedClasspath(consumer, evidence.resolve("classpath.txt"));
            Path log = run(ROOT, Map.of(), "platform-upload-" + graph,
                    List.of(java(), "-Xmx128m", "-Dfile.encoding=UTF-8", "-cp", uploadClasspath,
                            "example.PlatformUploadConsumer", graph, evidence.resolve("owned-files").toString()), 45, null);
            if (!Files.readString(log).contains("PLATFORM_UPLOAD_OK " + graph)) throw new AssertionError("Upload graph did not finish: " + log);
        }
        summary.add("platform-upload=real optional Tika absent/present; ordinary jar SafeUpload; actual byte budget/content MIME; missing required detector rejected; no test multipart class; staging cleaned");
    }

    static void platformConsumers() throws Exception {
        Path consumer = ROOT.resolve("verification/platform-consumer");
        Path inputs = Files.createDirectories(report.resolve("matrix-inputs"));
        Files.copy(consumer.resolve("pom.xml"), inputs.resolve("pom.xml"));
        copyDirectory(consumer.resolve("src"), inputs.resolve("src"));
        for (String graph : List.of("minimal", "no-jackson-module", "caffeine-only", "context-support-only", "cache-pair")) {
            Path evidence = Files.createDirectories(report.resolve("matrix/" + graph));
            maven(consumer, "matrix-build-" + graph, "-P" + graph, "clean", "compile", "dependency:build-classpath",
                    "-Dmdep.outputFile=" + evidence.resolve("classpath.txt"));
            maven(consumer, "matrix-model-" + graph, "-P" + graph, "help:effective-pom", "dependency:tree",
                    "-Doutput=" + evidence.resolve("effective-pom.xml"), "-DoutputFile=" + evidence.resolve("dependency-tree.txt"));
            String classpath = installedClasspath(consumer, evidence.resolve("classpath.txt"));
            var scenarios = graph.equals("minimal")
                    ? List.of("default", "disabled", "override", "jsons-override", "ambiguous", "primary", "virtual")
                    : List.of("default");
            for (String scenario : scenarios) {
                Path log = run(ROOT, Map.of(), "matrix-" + graph + "-" + scenario,
                        List.of(java(), "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", classpath,
                                "example.PlatformConsumer", graph, scenario), 45, null);
                String marker = "PLATFORM_CONSUMER_OK " + graph + " " + scenario;
                if (!Files.readString(log).contains(marker)) throw new AssertionError("Missing " + marker);
                summary.add(marker);
            }
        }
        summary.add("matrix=5 independent Maven production graphs; 11 JVM scenarios; ordinary installed jar equals this build; no test dependencies; -Xmx256m; 45s each");
    }

    static String installedClasspath(Path consumer, Path file) throws Exception {
        String dependencies = Files.readString(file).trim();
        boolean sameArtifact = false;
        for (String entry : dependencies.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path jar = Path.of(entry).toAbsolutePath().normalize();
            if (!entry.endsWith(".jar") || !jar.startsWith(repository)) {
                throw new AssertionError("Consumer dependency must be an isolated repository jar: " + entry);
            }
            if (jar.getFileName().toString().startsWith("server-facility-")) {
                Path built = ROOT.resolve("target").resolve(jar.getFileName());
                if (Files.mismatch(built, jar) != -1) throw new AssertionError("Consumer installed artifact differs from this build: " + jar);
                sameArtifact = true;
            }
        }
        if (!sameArtifact) throw new AssertionError("No verified library jar in consumer graph: " + file);
        return consumer.resolve("target/classes") + File.pathSeparator + dependencies;
    }

    static void prerequisites() throws Exception {
        // A separate copy/cache forces a real distribution download and checksum check; root Wrapper is untouched.
        var probe = Files.createDirectories(report.resolve("checksum-probe"));
        Files.createDirectories(probe.resolve(".mvn/wrapper"));
        for (String file : List.of("mvnw", "mvnw.cmd")) Files.copy(ROOT.resolve(file), probe.resolve(file));
        var original = Files.readString(ROOT.resolve(".mvn/wrapper/maven-wrapper.properties"));
        Files.writeString(probe.resolve(".mvn/wrapper/maven-wrapper.properties"),
                original.replaceAll("(?m)^distributionSha256Sum=.*$", "distributionSha256Sum=" + "0".repeat(64)));
        var checksum = wrapper(probe);
        checksum.add("--version");
        run(probe, Map.of("MAVEN_USER_HOME", probe.resolve("empty-home").toString()),
                "rejected-checksum", checksum, 180, "Failed to validate Maven distribution SHA-256");

        var missing = wrapper(ROOT);
        missing.add("validate");
        run(ROOT, Map.of("JAVA_HOME", report.resolve("missing-jdk").toString(),
                        "MAVEN_USER_HOME", wrapperHome.toString()),
                "rejected-missing-jdk", missing, 180, "JAVA_HOME");

        var wrongJava = System.getenv("VERIFY_WRONG_JAVA_HOME");
        if (wrongJava == null || !Files.isRegularFile(Path.of(wrongJava, "bin", WINDOWS ? "java.exe" : "java"))) {
            throw new IllegalStateException("Prerequisite verification requires VERIFY_WRONG_JAVA_HOME pointing to a real non-25 JDK (CI uses JDK 21); no tests were skipped.");
        }
        var wrong = wrapper(ROOT);
        wrong.addAll(List.of("-B", "-ntp", "-s", ROOT.resolve("verification/settings.xml").toString(),
                "-gs", ROOT.resolve("verification/settings.xml").toString(), "-Dmaven.repo.local=" + repository, "validate"));
        run(ROOT, Map.of("JAVA_HOME", wrongJava, "MAVEN_USER_HOME", wrapperHome.toString()),
                "rejected-wrong-jdk", wrong, 180, "server-facility requires JDK 25");
        summary.add("prerequisites=checksum rejected; missing JAVA_HOME rejected; real non-25 JDK rejected");
    }

    static void archiveQuality() throws Exception {
        Path reports = ROOT.resolve("target/surefire-reports");
        copyDirectory(reports, report.resolve("surefire-reports"));
        copyDirectory(ROOT.resolve("target/site/jacoco"), report.resolve("jacoco"));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        long tests = 0, failures = 0, errors = 0, skipped = 0;
        int architecture = 0;
        var architectureRules = new HashSet<String>();
        try (var files = Files.list(reports)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml")).toList()) {
                var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                tests += Long.parseLong(suite.getAttribute("tests"));
                failures += Long.parseLong(suite.getAttribute("failures"));
                errors += Long.parseLong(suite.getAttribute("errors"));
                skipped += Long.parseLong(suite.getAttribute("skipped"));
                if (suite.getAttribute("name").equals("cn.code91.facility.architecture.ArchitectureTest")) {
                    architecture += Integer.parseInt(suite.getAttribute("tests"));
                    var cases = suite.getElementsByTagName("testcase");
                    for (int i = 0; i < cases.getLength(); i++) {
                        architectureRules.add(((Element) cases.item(i)).getAttribute("name"));
                    }
                }
            }
        }
        summary.add("tests=" + tests + " failures=" + failures + " errors=" + errors + " skipped=" + skipped + " architecture=" + architecture);
        var requiredRules = Set.of("packages_are_cycle_free", "error_package_depends_only_on_jdk",
                "main_code_does_not_depend_on_logback", "autoconfigure_is_not_depended_on_by_main_packages",
                "excel_facade_does_not_depend_on_poi");
        if (tests == 0 || failures != 0 || errors != 0 || skipped != 0 || !architectureRules.containsAll(requiredRules)) {
            throw new AssertionError("Test discovery/quality check failed; inspect Surefire evidence. " + summary.getLast());
        }
        var jacoco = factory.newDocumentBuilder().parse(ROOT.resolve("target/site/jacoco/jacoco.xml").toFile());
        for (var child = jacoco.getDocumentElement().getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element counter && counter.getTagName().equals("counter")) {
                long missed = Long.parseLong(counter.getAttribute("missed"));
                long covered = Long.parseLong(counter.getAttribute("covered"));
                summary.add("coverage " + counter.getAttribute("type") + "=" + covered + "/" + (covered + missed));
            }
        }
        try (var files = Files.list(ROOT.resolve("target"))) {
            for (Path jar : files.filter(p -> p.toString().endsWith(".jar")).toList()) {
                Files.copy(jar, Files.createDirectories(report.resolve("artifacts")).resolve(jar.getFileName()));
                summary.add("sha256 " + jar.getFileName() + "=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
            }
        }
    }

    static void copyDirectory(Path source, Path destination) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path to = destination.resolve(source.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(to);
                else Files.copy(file, to);
            }
        }
    }

    static Path run(Path cwd, Map<String, String> environment, String name, List<String> command,
                    long timeoutSeconds, String expectedFailure) throws Exception {
        Path log = report.resolve(String.format("%02d-%s.log", ++sequence, name));
        System.out.println("Running " + name + "; log=" + log);
        summary.add("command " + name + " cwd=" + cwd + " args=" + command);
        var builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        var process = builder.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw new AssertionError(name + " exceeded " + timeoutSeconds + "s; process tree terminated; inspect " + log);
        }
        int exit = process.exitValue();
        summary.add(name + " exit=" + exit + (expectedFailure == null ? "" : " expected failure=" + expectedFailure));
        // Windows PowerShell can localize surrounding errors using the console code page.
        // Keep the raw log, and decode ASCII diagnostic markers without rejecting non-UTF-8 bytes.
        String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        boolean diagnosticFound = expectedFailure != null && output.replaceAll("\\s+", "")
                .contains(expectedFailure.replaceAll("\\s+", ""));
        if (expectedFailure == null ? exit != 0 : exit == 0 || !diagnosticFound) {
            System.err.println(output);
            if ("true".equals(System.getenv("GITHUB_ACTIONS"))) {
                // Public check annotations keep a bounded failure tail available alongside the archived full log.
                String tail = output.substring(Math.max(0, output.length() - 10000));
                System.err.println("::error title=Verification failure detail::" + tail.replace("%", "%25")
                        .replace("\r", "%0D").replace("\n", "%0A"));
            }
            throw new AssertionError("Unexpected result for " + name + "; exit=" + exit + "; inspect " + log);
        }
        return log;
    }
}
