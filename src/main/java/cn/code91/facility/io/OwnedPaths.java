package cn.code91.facility.io;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Link rejection in application-owned namespaces; this is not a hostile-rename sandbox. */
final class OwnedPaths {
    private OwnedPaths() { }

    static void directories(Path path, boolean create) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        requireDirectory(current, false);
        for (Path component : absolute) {
            current = current.resolve(component);
            requireDirectory(current, create);
        }
    }

    private static void requireDirectory(Path directory, boolean create) throws IOException {
        BasicFileAttributes attrs;
        try { attrs = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException missing) {
            if (!create) throw missing;
            try { Files.createDirectory(directory); }
            catch (FileAlreadyExistsException concurrentCreation) { /* Validate the existing node below. */ }
            attrs = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        requireOrdinary(directory, attrs);
        if (!attrs.isDirectory()) throw new IOException("Expected a real directory: " + directory);
    }

    static void regularFile(Path file) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        directories(absolute.getParent(), false);
        var attrs = Files.readAttributes(absolute, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        requireOrdinary(absolute, attrs);
        if (!attrs.isRegularFile()) throw new IOException("Expected a regular file: " + file);
    }

    static void requireOrdinary(Path path, BasicFileAttributes attrs) throws IOException {
        if (attrs.isSymbolicLink() || attrs.isOther()
                || !path.toRealPath().equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Links and special filesystem entries are not supported: " + path);
        }
    }
}
