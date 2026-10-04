import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

/** Checks the delivered application copy, without repository-relative documentation dependencies. */
class TemplateLineage {
    public static void main(String[] args) throws Exception {
        Path application = (args[0].startsWith("file:") ? Path.of(URI.create(args[0])) : Path.of(args[0])).toAbsolutePath().normalize();
        var origin = new Properties();
        try (var input = Files.newInputStream(application.resolve("template-origin.properties"))) {
            origin.load(input);
        }
        if (!"secured-api".equals(origin.getProperty("template.id"))
                || !"2026.10.0".equals(origin.getProperty("template.revision"))
                || !"cn.code91:server-facility:0.2.0-SNAPSHOT".equals(origin.getProperty("runtime.coordinate")))
            throw new AssertionError("The independent application must carry its template and runtime line separately");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(application.resolve("pom.xml").toFile());
        if (!"0.2.0-SNAPSHOT".equals(document.getElementsByTagName("facility.version").item(0).getTextContent()))
            throw new AssertionError("The application dependency does not match its declared runtime line");
        var link = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)");
        int checked = 0;
        try (var files = Files.walk(application)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                var links = link.matcher(Files.readString(file));
                while (links.find()) {
                    String target = links.group(1).split("#", 2)[0];
                    if (target.isEmpty() || target.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) continue;
                    Path resolved = file.getParent().resolve(target).normalize();
                    if (!resolved.startsWith(application) || !Files.exists(resolved))
                        throw new AssertionError("Application-local documentation link is unavailable: " + file + " -> " + target);
                    checked++;
                }
            }
        }
        System.out.println("PASS independent template lineage and " + checked + " local documentation links");
    }
}
