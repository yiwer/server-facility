package cn.code91.facility.web.util;

import org.jsoup.safety.Safelist;
import java.util.function.Supplier;

/**
 * Predefined Jsoup Safelist presets. Pass to {@link XssUtil#clean(String, XssLevel)}.
 */
public enum XssLevel {
    NONE(Safelist::none),
    BASIC(Safelist::basic),
    BASIC_WITH_IMAGES(Safelist::basicWithImages),
    RELAXED(Safelist::relaxed);

    private final Supplier<Safelist> factory;
    XssLevel(Supplier<Safelist> factory) { this.factory = factory; }
    Safelist safelist() { return factory.get(); }
}
