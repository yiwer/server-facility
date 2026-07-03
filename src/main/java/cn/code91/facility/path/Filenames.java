package cn.code91.facility.path;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * <b>文件名安全工具</b>
 * <p>路径穿越防御、扩展名提取、危险扩展名识别。纯字符串操作，无 IO。</p>
 */
public final class Filenames {

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "ps1", "vbs", "js",
            "jar", "msi", "dll", "com", "scr", "pif",
            "jsp", "jspx", "jspf", "php", "php3", "php4", "php5", "phtml",
            "asp", "aspx", "jsf", "cgi", "pl", "py", "rb"
    );

    private Filenames() { throw new UnsupportedOperationException(); }

    /**
     * 清洗文件名：去掉路径前缀、检测 {@code ..}、替换不安全字符。
     */
    public static Result<String, WrappedError> sanitize(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID));
        }

        String cleaned = StringUtils.cleanPath(fileName);
        if (cleaned.contains("..")) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_NAME_INVALID, null, new Object[]{fileName}));
        }

        int lastSeparator = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            cleaned = cleaned.substring(lastSeparator + 1);
        }

        cleaned = cleaned.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");

        if (!StringUtils.hasText(cleaned)) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID));
        }
        return Result.ok(cleaned);
    }

    /**
     * 取扩展名（不带点）。{@code "test.jpg"} → {@code "jpg"}；无扩展名返回 {@code null}。
     */
    public static String extension(String fileName) {
        return StringUtils.getFilenameExtension(fileName);
    }

    /**
     * 取不带扩展名的文件名。
     */
    public static String nameWithoutExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static boolean isDangerousExtension(String fileName) {
        String ext = extension(fileName);
        return ext != null && DANGEROUS_EXTENSIONS.contains(ext.toLowerCase());
    }

    public static boolean checkExtension(String fileName, String... allowedExtensions) {
        String ext = extension(fileName);
        if (!StringUtils.hasText(ext) || allowedExtensions == null) {
            return false;
        }
        return Arrays.stream(allowedExtensions)
                .anyMatch(allowed -> allowed.equalsIgnoreCase(ext));
    }

    public static boolean checkExtension(String fileName, Set<String> allowedExtensions) {
        String ext = extension(fileName);
        if (!StringUtils.hasText(ext) || allowedExtensions == null) {
            return false;
        }
        return allowedExtensions.stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(ext));
    }
}
