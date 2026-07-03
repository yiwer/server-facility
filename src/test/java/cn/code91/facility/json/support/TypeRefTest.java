package cn.code91.facility.json.support;

import cn.code91.facility.json.Jsons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TypeRef - 泛型类型引用工厂")
class TypeRefTest {

    private final Jsons jsons = new Jsons(JsonConfig.standard().build());

    @Test
    void ofList_deserializesTypedList() {
        List<Integer> list = jsons.deserialize("[1,2,3]", TypeRef.ofList(Integer.class)).get();
        assertThat(list).containsExactly(1, 2, 3);
    }

    @Test
    void ofSet_deserializesTypedSet() {
        var set = jsons.deserialize("[1,2,2,3]", TypeRef.ofSet(Integer.class)).get();
        assertThat(set).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void ofMap_deserializesTypedMap() {
        Map<String, Integer> map = jsons.deserialize(
                "{\"a\":1,\"b\":2}", TypeRef.ofMap(String.class, Integer.class)).get();
        assertThat(map).containsEntry("a", 1).containsEntry("b", 2);
    }

    @Test
    void ofStringListMap_nestedGenerics() {
        Map<String, List<Integer>> map = jsons.deserialize(
                "{\"xs\":[1,2]}", TypeRef.ofStringListMap(Integer.class)).get();
        assertThat(map.get("xs")).containsExactly(1, 2);
    }

    @Test
    void ofClass_plainType() {
        Integer value = jsons.deserialize("42", TypeRef.of(Integer.class)).get();
        assertThat(value).isEqualTo(42);
    }

    // ==================== P7-T3 补测:静态工厂盲区 ====================

    @Test
    void ofStringMap_deserializesTypedMap() {
        Map<String, Integer> map = jsons.deserialize(
                "{\"a\":1,\"b\":2}", TypeRef.ofStringMap(Integer.class)).get();
        assertThat(map).containsEntry("a", 1).containsEntry("b", 2);
    }

    @Test
    void ofListStringMap_nestedGenerics() {
        List<Map<String, Integer>> list = jsons.deserialize(
                "[{\"a\":1},{\"b\":2}]", TypeRef.ofListStringMap(Integer.class)).get();
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("a", 1);
        assertThat(list.get(1)).containsEntry("b", 2);
    }

    // ==================== P7-T3 补测:protected 无参构造(匿名子类直接使用,不经静态工厂) ====================

    @Test
    @DisplayName("匿名子类直接捕获泛型类型(javadoc 方式1),可用于反序列化")
    void anonymousSubclass_capturesGenericType_deserializes() {
        List<String> list = jsons.deserialize("[\"a\",\"b\"]", new TypeRef<List<String>>() {}).get();
        assertThat(list).containsExactly("a", "b");
    }

    @Test
    @DisplayName("非匿名子类(raw 类型使用,无法捕获泛型参数)抛 IllegalArgumentException")
    void rawSubclass_throwsIllegalArgumentException() {
        assertThatThrownBy(RawTypeRef::new).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("rawtypes")
    private static class RawTypeRef extends TypeRef {
    }

    // ==================== P7-T3 补测:ParameterizedTypeImpl 内部类盲区 ====================

    @Test
    @DisplayName("getType() 返回的 ParameterizedType 的 getOwnerType 恒为 null(顶层类型,无外部类)")
    void parameterizedType_ownerTypeIsNull() {
        ParameterizedType type = (ParameterizedType) TypeRef.ofList(Integer.class).getType();
        assertThat(type.getOwnerType()).isNull();
    }

    @Test
    @DisplayName("getType() 返回的 ParameterizedType toString 呈现 raw<arg1, arg2> 形状")
    void parameterizedType_toStringShape() {
        ParameterizedType type = (ParameterizedType) TypeRef.ofMap(String.class, Integer.class).getType();
        assertThat(type.toString()).isEqualTo("java.util.Map<java.lang.String, java.lang.Integer>");
    }
}
