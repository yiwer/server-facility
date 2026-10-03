import java.nio.file.*;
import java.net.URI;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** JDK-only copy operation; each argument accepts a filesystem path or an ASCII-encoded file URI. */
class Instantiate {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply template directory and a new destination directory");
        Path source = path(args[0]).toAbsolutePath().normalize().toRealPath();
        Path destination = path(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source.resolve("pom.xml")) || destination.startsWith(source) || Files.exists(destination))
            throw new IllegalArgumentException("Destination must be new and outside the template directory");
        Files.createDirectories(destination);
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path relative = source.relativize(file);
                if (relative.toString().isEmpty()) continue;
                if (Set.of("target", ".git", ".verification-results", ".local", ".idea").contains(relative.getName(0).toString())) continue;
                if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Template symbolic links are unsupported");
                Path copy = destination.resolve(relative);
                if (Files.isDirectory(file)) Files.createDirectories(copy);
                else Files.copy(file, copy);
            }
        }
        if (Files.getFileStore(destination).supportsFileAttributeView("posix")) {
            var permissions = Files.getPosixFilePermissions(destination.resolve("mvnw"));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination.resolve("mvnw"), permissions);
        }
        System.out.println("Created independent application: " + destination);
    }

    private static Path path(String value) {
        return value.regionMatches(true, 0, "file:", 0, 5) ? Path.of(URI.create(value)) : Path.of(value);
    }
}
