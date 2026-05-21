package aster.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StdMap Map 辅助方法的最小基线测试。
 *
 * 验证 put 的不可变语义（不修改源 Map）与 null 源的鲁棒性。
 */
class StdMapTest {

  @Test
  void emptyReturnsEmptyMutableMap() {
    Map<Object, Object> map = StdMap.empty();
    assertNotNull(map);
    assertTrue(map.isEmpty());
  }

  @Test
  void putProducesNewMapWithoutMutatingSource() {
    Map<Object, Object> source = StdMap.empty();
    source.put("a", 1);
    Map<Object, Object> result = StdMap.put(source, "b", 2);
    assertEquals(Map.of("a", 1, "b", 2), result);
    assertEquals(Map.of("a", 1), source, "put 不应改变源 Map");
  }

  @Test
  void putHandlesNonMapSource() {
    // emitter 偶尔会把 null 或非 Map 作为 source；按设计应返回仅含 (k,v) 的 Map
    Map<Object, Object> result = StdMap.put(null, "k", "v");
    assertEquals(Map.of("k", "v"), result);
  }

  @Test
  void putOverridesExistingKey() {
    Map<Object, Object> source = StdMap.empty();
    source.put("a", 1);
    Map<Object, Object> result = StdMap.put(source, "a", 2);
    assertEquals(2, result.get("a"));
  }
}
