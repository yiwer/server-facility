package cn.code91.facility.path;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Filenames - 文件名安全")
class FilenamesTest {

    @Test
    void sanitize_plainName_passesThrough() {
        assertThat(Filenames.sanitize("report_2026.pdf").get()).isEqualTo("report_2026.pdf");
    }

    @Test
    void sanitize_stripsPathPrefixes_bothSeparators() {
        assertThat(Filenames.sanitize("dir/sub/name.txt").get()).isEqualTo("name.txt");
        assertThat(Filenames.sanitize("dir\\sub\\name.txt").get()).isEqualTo("name.txt");
    }

    @Test
    void sanitize_rejectsTraversal() {
        assertThat(Filenames.sanitize("../../etc/passwd").getErr()
                .isErrorType(FacilityErrorType.FILE_NAME_INVALID)).isTrue();
    }

    @Test
    void sanitize_replacesUnsafeChars() {
        assertThat(Filenames.sanitize("a<b>c:d\"e|f?g*h.txt").get()).isEqualTo("a_b_c_d_e_f_g_h.txt");
    }

    @Test
    void sanitize_blank_returnsInvalid() {
        assertThat(Filenames.sanitize("   ").getErr()
                .isErrorType(FacilityErrorType.FILE_NAME_INVALID)).isTrue();
        assertThat(Filenames.sanitize(null).getErr()
                .isErrorType(FacilityErrorType.FILE_NAME_INVALID)).isTrue();
    }

    @Test
    void extension_extractsWithoutDot_orNull() {
        assertThat(Filenames.extension("photo.JPG")).isEqualTo("JPG");
        assertThat(Filenames.extension("README")).isNull();
    }

    @Test
    void nameWithoutExtension_boundaries() {
        assertThat(Filenames.nameWithoutExtension("photo.jpg")).isEqualTo("photo");
        assertThat(Filenames.nameWithoutExtension(".gitignore")).isEqualTo(".gitignore");
        assertThat(Filenames.nameWithoutExtension("plain")).isEqualTo("plain");
    }

    @Test
    void isDangerousExtension_caseInsensitive() {
        assertThat(Filenames.isDangerousExtension("virus.EXE")).isTrue();
        assertThat(Filenames.isDangerousExtension("safe.txt")).isFalse();
        assertThat(Filenames.isDangerousExtension("noext")).isFalse();
    }

    @Test
    void checkExtension_varargs_caseInsensitive() {
        assertThat(Filenames.checkExtension("a.PNG", "jpg", "png")).isTrue();
        assertThat(Filenames.checkExtension("a.gif", "jpg", "png")).isFalse();
        assertThat(Filenames.checkExtension("noext", "jpg")).isFalse();
    }

    @Test
    void checkExtension_set_caseInsensitive() {
        assertThat(Filenames.checkExtension("a.WebP", Set.of("webp"))).isTrue();
        assertThat(Filenames.checkExtension("a.webp", (Set<String>) null)).isFalse();
    }
}
