package cn.code91.facility.copy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * CopyUtil.copyList / copySet / copyMapValues / copyMapAll 行为盲区补测(债4)。
 * 覆盖:null 输入、默认/显式 CopyOptions、自定义拷贝函数、null 元素跳过策略、
 * copy() 违约返回 null 时的异常契约。
 */
@DisplayName("CopyUtil - copyList/copySet/copyMapValues/copyMapAll 集合拷贝盲区补测(债4)")
class CopyUtilCollectionsTest {

    /** 拷贝时产生新实例但保留 tag 值,用于区分“值相等”与“同一实例”。 */
    static final class Item implements CopyTrait<Item> {
        final String tag;
        Item(String tag) { this.tag = tag; }
        @Override public Item copy() { return new Item(tag); }
        @Override public String toString() { return "Item(" + tag + ")"; }
    }

    /** copy() 违约返回 null,用于触发 validateCopied 的抛异常/跳过分支。 */
    static final class NullCopyItem implements CopyTrait<NullCopyItem> {
        @Override public NullCopyItem copy() { return null; }
    }

    @Nested
    @DisplayName("copyList")
    class CopyListTests {

        @Test
        @DisplayName("null 输入返回空列表(不返回 null)")
        void nullInput_returnsEmptyList() {
            assertThat(CopyUtil.<Item>copyList(null)).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("默认选项:逐元素深拷贝,值相等但非同一实例")
        void defaultOptions_deepCopiesElements() {
            List<Item> origin = List.of(new Item("a"), new Item("b"));
            List<Item> copy = CopyUtil.copyList(origin);
            assertThat(copy).hasSize(2);
            assertThat(copy.get(0).tag).isEqualTo("a");
            assertThat(copy.get(0)).isNotSameAs(origin.get(0));
            assertThat(copy.get(1).tag).isEqualTo("b");
            assertThat(copy.get(1)).isNotSameAs(origin.get(1));
        }

        @Test
        @DisplayName("显式选项 + null 元素:skipNullElements=false(默认)保留 null 占位")
        void explicitOptions_nullElementKept_whenSkipFalse() {
            List<Item> origin = new ArrayList<>();
            origin.add(new Item("a"));
            origin.add(null);
            List<Item> copy = CopyUtil.copyList(origin, CopyUtil.CopyOptions.builder().build());
            assertThat(copy).hasSize(2);
            assertThat(copy.get(1)).isNull();
        }

        @Test
        @DisplayName("显式选项 + null 元素:skipNullElements=true 跳过 null")
        void explicitOptions_nullElementSkipped_whenSkipTrue() {
            List<Item> origin = new ArrayList<>();
            origin.add(new Item("a"));
            origin.add(null);
            List<Item> copy = CopyUtil.copyList(origin,
                    CopyUtil.CopyOptions.builder().skipNullElements(true).build());
            assertThat(copy).hasSize(1);
            assertThat(copy.get(0).tag).isEqualTo("a");
        }

        @Test
        @DisplayName("copy() 违约返回 null 且 throwOnNullCopy=true(默认):抛 CopyException")
        void copyReturnsNull_throwsByDefault() {
            List<NullCopyItem> origin = List.of(new NullCopyItem());
            assertThatExceptionOfType(CopyUtil.CopyException.class)
                    .isThrownBy(() -> CopyUtil.copyList(origin))
                    .withMessageContaining("violates the contract");
        }

        @Test
        @DisplayName("自定义拷贝函数:逐元素应用函数")
        void customFunction_appliesPerElement() {
            List<String> origin = List.of("a", "b");
            List<String> copy = CopyUtil.copyList(origin, s -> s + "!");
            assertThat(copy).containsExactly("a!", "b!");
        }

        @Test
        @DisplayName("自定义拷贝函数 + 显式选项:null 元素 skipNullElements=false 时保留")
        void customFunctionWithOptions_nullElementKept_whenSkipFalse() {
            List<String> origin = new ArrayList<>();
            origin.add("a");
            origin.add(null);
            List<String> copy = CopyUtil.copyList(origin, s -> s + "!",
                    CopyUtil.CopyOptions.builder().build());
            assertThat(copy).hasSize(2);
            assertThat(copy.get(0)).isEqualTo("a!");
            assertThat(copy.get(1)).isNull();
        }

        @Test
        @DisplayName("自定义拷贝函数 + 显式选项:null 元素 skipNullElements=true 时跳过")
        void customFunctionWithOptions_nullElementSkipped_whenSkipTrue() {
            List<String> origin = new ArrayList<>();
            origin.add("a");
            origin.add(null);
            List<String> copy = CopyUtil.copyList(origin, s -> s + "!",
                    CopyUtil.CopyOptions.builder().skipNullElements(true).build());
            assertThat(copy).containsExactly("a!");
        }
    }

    @Nested
    @DisplayName("copySet")
    class CopySetTests {

        @Test
        @DisplayName("null 输入返回空 Set")
        void nullInput_returnsEmptySet() {
            assertThat(CopyUtil.<Item>copySet(null)).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("默认选项:逐元素深拷贝")
        void defaultOptions_deepCopiesElements() {
            Set<Item> origin = new HashSet<>();
            origin.add(new Item("a"));
            Set<Item> copy = CopyUtil.copySet(origin);
            assertThat(copy).hasSize(1);
            Item copied = copy.iterator().next();
            assertThat(copied.tag).isEqualTo("a");
            assertThat(copied).isNotSameAs(origin.iterator().next());
        }

        @Test
        @DisplayName("显式选项 + null 元素:skipNullElements=true 跳过")
        void explicitOptions_nullElementSkipped() {
            Set<Item> origin = new HashSet<>();
            origin.add(null);
            origin.add(new Item("a"));
            Set<Item> copy = CopyUtil.copySet(origin,
                    CopyUtil.CopyOptions.builder().skipNullElements(true).build());
            assertThat(copy).hasSize(1);
            assertThat(copy.iterator().next().tag).isEqualTo("a");
        }

        @Test
        @DisplayName("显式选项 + null 元素:skipNullElements=false(默认)保留 null")
        void explicitOptions_nullElementKept() {
            Set<Item> origin = new HashSet<>();
            origin.add(null);
            Set<Item> copy = CopyUtil.copySet(origin, CopyUtil.CopyOptions.builder().build());
            assertThat(copy).hasSize(1);
            assertThat(copy).containsNull();
        }

        @Test
        @DisplayName("自定义拷贝函数:逐元素应用函数")
        void customFunction_appliesPerElement() {
            Set<String> origin = new HashSet<>();
            origin.add("a");
            Set<String> copy = CopyUtil.copySet(origin, s -> s + "!");
            assertThat(copy).containsExactly("a!");
        }

        @Test
        @DisplayName("自定义拷贝函数 + 显式选项:null 元素跳过")
        void customFunctionWithOptions_nullElementSkipped() {
            Set<String> origin = new HashSet<>();
            origin.add(null);
            origin.add("a");
            Set<String> copy = CopyUtil.copySet(origin, s -> s + "!",
                    CopyUtil.CopyOptions.builder().skipNullElements(true).build());
            assertThat(copy).containsExactly("a!");
        }
    }

    @Nested
    @DisplayName("copyMapValues")
    class CopyMapValuesTests {

        @Test
        @DisplayName("null 输入返回空 Map")
        void nullInput_returnsEmptyMap() {
            assertThat(CopyUtil.<String, Item>copyMapValues(null)).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("默认选项:key 原样保留,value 深拷贝")
        void defaultOptions_keyKept_valueDeepCopied() {
            Map<String, Item> origin = new HashMap<>();
            Item value = new Item("v");
            origin.put("k", value);
            Map<String, Item> copy = CopyUtil.copyMapValues(origin);
            assertThat(copy).containsKey("k");
            assertThat(copy.get("k").tag).isEqualTo("v");
            assertThat(copy.get("k")).isNotSameAs(value);
        }

        @Test
        @DisplayName("显式选项 + null Map:返回空 Map")
        void explicitOptions_nullMap_returnsEmptyMap() {
            Map<String, Item> copy = CopyUtil.copyMapValues(null, CopyUtil.CopyOptions.builder().build());
            assertThat(copy).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("value 为 null 且 skipNullElements=false(默认):保留 null 值")
        void nullValue_kept_whenSkipFalse() {
            Map<String, Item> origin = new HashMap<>();
            origin.put("k", null);
            Map<String, Item> copy = CopyUtil.copyMapValues(origin, CopyUtil.CopyOptions.builder().build());
            assertThat(copy).containsKey("k");
            assertThat(copy.get("k")).isNull();
        }

        @Test
        @DisplayName("value 为 null 且 skipNullElements=true:整条 entry 跳过")
        void nullValue_skipped_whenSkipTrue() {
            Map<String, Item> origin = new HashMap<>();
            origin.put("k", null);
            Map<String, Item> copy = CopyUtil.copyMapValues(origin,
                    CopyUtil.CopyOptions.builder().skipNullElements(true).build());
            assertThat(copy).doesNotContainKey("k");
            assertThat(copy).isEmpty();
        }
    }

    @Nested
    @DisplayName("copyMapAll")
    class CopyMapAllTests {

        @Test
        @DisplayName("null 输入返回空 Map")
        void nullInput_returnsEmptyMap() {
            assertThat(CopyUtil.<Item, Item>copyMapAll(null)).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("显式选项 + null Map:返回空 Map")
        void explicitOptions_nullMap_returnsEmptyMap() {
            Map<Item, Item> copy = CopyUtil.copyMapAll(null, CopyUtil.CopyOptions.builder().build());
            assertThat(copy).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("默认选项:key 和 value 均深拷贝")
        void defaultOptions_bothKeyAndValueDeepCopied() {
            Map<Item, Item> origin = new HashMap<>();
            Item key = new Item("k");
            Item value = new Item("v");
            origin.put(key, value);
            Map<Item, Item> copy = CopyUtil.copyMapAll(origin);
            assertThat(copy).hasSize(1);
            Map.Entry<Item, Item> entry = copy.entrySet().iterator().next();
            assertThat(entry.getKey().tag).isEqualTo("k");
            assertThat(entry.getKey()).isNotSameAs(key);
            assertThat(entry.getValue().tag).isEqualTo("v");
            assertThat(entry.getValue()).isNotSameAs(value);
        }

        @Test
        @DisplayName("key 为 null 且 throwOnNullCopy=true(默认):抛 CopyException")
        void nullKey_throwsByDefault() {
            Map<Item, Item> origin = new HashMap<>();
            origin.put(null, new Item("v"));
            assertThatExceptionOfType(CopyUtil.CopyException.class)
                    .isThrownBy(() -> CopyUtil.copyMapAll(origin))
                    .withMessage("Map key cannot be null");
        }

        @Test
        @DisplayName("key 为 null 且 throwOnNullCopy=false:该 entry 跳过,不抛异常,其余 entry 正常")
        void nullKey_skipped_whenThrowFalse() {
            Map<Item, Item> origin = new HashMap<>();
            origin.put(null, new Item("v"));
            origin.put(new Item("k2"), new Item("v2"));
            Map<Item, Item> copy = CopyUtil.copyMapAll(origin,
                    CopyUtil.CopyOptions.builder().throwOnNullCopy(false).build());
            assertThat(copy).hasSize(1);
            Map.Entry<Item, Item> entry = copy.entrySet().iterator().next();
            assertThat(entry.getKey().tag).isEqualTo("k2");
            assertThat(entry.getValue().tag).isEqualTo("v2");
        }

        @Test
        @DisplayName("F5:宽容模式下 null key entry 丢弃但必须 WARN(不再静默),条目数 3 进 2 出")
        void nullKey_droppedWithWarn_whenThrowFalse() {
            ch.qos.logback.classic.Logger root =
                    (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                    new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            root.addAppender(appender);
            try {
                Map<Item, Item> origin = new HashMap<>();
                origin.put(null, new Item("v"));
                origin.put(new Item("k2"), new Item("v2"));
                origin.put(new Item("k3"), new Item("v3"));
                Map<Item, Item> copy = CopyUtil.copyMapAll(origin,
                        CopyUtil.CopyOptions.builder().throwOnNullCopy(false).build());
                assertThat(copy).hasSize(2);
                assertThat(appender.list).anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                    assertThat(e.getFormattedMessage()).contains("null map key entry dropped");
                });
            } finally {
                root.detachAppender(appender);
            }
        }

        @Test
        @DisplayName("value 为 null 且 skipNullElements=false(默认):key 仍深拷贝,value 保留 null")
        void nullValue_kept_whenSkipFalse() {
            Map<Item, Item> origin = new HashMap<>();
            Item key = new Item("k");
            origin.put(key, null);
            Map<Item, Item> copy = CopyUtil.copyMapAll(origin);
            assertThat(copy).hasSize(1);
            Map.Entry<Item, Item> entry = copy.entrySet().iterator().next();
            assertThat(entry.getKey().tag).isEqualTo("k");
            assertThat(entry.getKey()).isNotSameAs(key);
            assertThat(entry.getValue()).isNull();
        }

        @Test
        @DisplayName("value 为 null 且 skipNullElements=true:整条 entry 跳过")
        void nullValue_skipped_whenSkipTrue() {
            Map<Item, Item> origin = new HashMap<>();
            origin.put(new Item("k"), null);
            Map<Item, Item> copy = CopyUtil.copyMapAll(origin,
                    CopyUtil.CopyOptions.builder().skipNullElements(true).build());
            assertThat(copy).isEmpty();
        }
    }
}
