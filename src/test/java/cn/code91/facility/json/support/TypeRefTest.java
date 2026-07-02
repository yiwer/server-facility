package cn.code91.facility.json.support;

import cn.code91.facility.json.Jsons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
