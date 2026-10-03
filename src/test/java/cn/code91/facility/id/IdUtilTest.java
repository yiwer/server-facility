package cn.code91.facility.id;

import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdUtil ID 生成工具类测试")
class IdUtilTest {

    // ==================== snowId ====================

    @Nested
    @DisplayName("snowId 雪花算法 ID")
    class SnowIdTests {

        @Test
        @DisplayName("生成的 ID 非 null")
        void snowId_notNull() {
            Long id = IdUtil.snowId();
            assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("生成的 ID 为正数")
        void snowId_positive() {
            Long id = IdUtil.snowId();
            assertThat(id).isPositive();
        }

        @Test
        @DisplayName("1000 次调用生成的 ID 全部唯一")
        void snowId_1000_allUnique() {
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(IdUtil.snowId());
            }
            assertThat(ids).hasSize(1000);
        }
    }

    // ==================== uuid ====================

    @Nested
    @DisplayName("uuid UUID 生成")
    class UuidTests {

        @Test
        @DisplayName("uuid() 返回非 null 的 UUID 对象")
        void uuid_notNull() {
            UUID uuid = IdUtil.uuid();
            assertThat(uuid).isNotNull();
        }

        @Test
        @DisplayName("uuid() 两次调用返回不同值")
        void uuid_unique() {
            UUID u1 = IdUtil.uuid();
            UUID u2 = IdUtil.uuid();
            assertThat(u1).isNotEqualTo(u2);
        }
    }

    // ==================== uuidStr ====================

    @Nested
    @DisplayName("uuidStr UUID 字符串")
    class UuidStrTests {

        @Test
        @DisplayName("uuidStr() 返回非 null 字符串")
        void uuidStr_notNull() {
            String str = IdUtil.uuidStr();
            assertThat(str).isNotNull();
        }

        @Test
        @DisplayName("uuidStr() 返回长度为 36 的标准 UUID 格式 (含连字符)")
        void uuidStr_length36() {
            String str = IdUtil.uuidStr();
            assertThat(str).hasSize(36);
        }

        @Test
        @DisplayName("uuidStr() 格式正确: 8-4-4-4-12")
        void uuidStr_format() {
            String str = IdUtil.uuidStr();
            assertThat(str).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("uuidStr() 两次调用返回不同值")
        void uuidStr_unique() {
            String s1 = IdUtil.uuidStr();
            String s2 = IdUtil.uuidStr();
            assertThat(s1).isNotEqualTo(s2);
        }
    }

    // ==================== uuidSimpleStr ====================

    @Nested
    @DisplayName("uuidSimpleStr 不含连字符的 UUID")
    class UuidSimpleStrTests {

        @Test
        @DisplayName("uuidSimpleStr() 返回长度为 32 的字符串 (无连字符)")
        void uuidSimpleStr_length32() {
            String str = IdUtil.uuidSimpleStr();
            assertThat(str).hasSize(32);
            assertThat(str).doesNotContain("-");
        }
    }

    @Nested
    @DisplayName("IdUtil.parseTimestamp / parseInfo 通过 setGenerator 注入自定义 epoch (phase-4 RP-15 / ADR-0008)")
    class CustomEpochFacadeTests {

        @AfterEach
        void resetGen() {
            IdUtil.resetGenerator();
        }

        @Test
        @DisplayName("setGenerator 注入自定义 epoch generator 后，IdUtil.parseTimestamp 通过实例正确解析")
        void facadeParseTimestampWithCustomEpoch() {
            long customEpoch = 1_700_000_000_000L;
            SnowIdGenerator custom = new SnowIdGenerator(0, 0, customEpoch);
            IdUtil.setGenerator(custom);

            long beforeGen = System.currentTimeMillis();
            long id = IdUtil.snowId();
            long afterGen = System.currentTimeMillis();
            long parsed = IdUtil.parseTimestamp(id);

            assertThat(parsed).isBetween(beforeGen, afterGen);
        }

        @Test
        @DisplayName("setGenerator 注入自定义 epoch 后 IdUtil.parseInfo 反映正确时间戳")
        void facadeParseInfoWithCustomEpoch() {
            long customEpoch = 1_700_000_000_000L;
            SnowIdGenerator custom = new SnowIdGenerator(0, 0, customEpoch);
            IdUtil.setGenerator(custom);

            long id = IdUtil.snowId();
            String info = IdUtil.parseInfo(id);
            long parsedTs = IdUtil.parseTimestamp(id);

            assertThat(info).contains("timestamp=" + parsedTs);
        }
    }
}
