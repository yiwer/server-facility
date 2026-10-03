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
        if (!Set.of("fast", "integration", "resources", "all", "prerequisites").contains(mode)) {
            throw new IllegalArgumentException("Use fast, integration, resources, all or prerequisites; optional --fresh.");
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
            if (mode.equals("prerequisites")) {
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
        var command = wrapper(directory);
        command.addAll(List.of("-B", "-ntp", "-C", "-s", ROOT.resolve("verification/settings.xml").toString(),
                "-gs", ROOT.resolve("verification/settings.xml").toString(), "-Dmaven.repo.local=" + repository));
        command.addAll(List.of(goals));
        run(directory, Map.of("MAVEN_USER_HOME", wrapperHome.toString()), name, command, 1200, null);
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
            throw new AssertionError("Unexpected result for " + name + "; exit=" + exit + "; inspect " + log);
        }
        return log;
    }
}
