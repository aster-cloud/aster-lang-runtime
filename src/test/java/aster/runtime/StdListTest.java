package aster.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StdList 列表辅助方法的最小基线测试。
 *
 * 验证不可变追加语义、map/filter 正确性与谓词类型约束。
 */
class StdListTest {

  @Test
  void emptyReturnsEmptyMutableList() {
    List<Object> list = StdList.empty();
    assertNotNull(list);
    assertTrue(list.isEmpty());
  }

  @Test
  void appendProducesNewListWithoutMutatingSource() {
    List<Object> source = List.of(1, 2);
    List<Object> result = StdList.append(source, 3);
    assertEquals(List.of(1, 2, 3), result);
    assertEquals(List.of(1, 2), source, "append 不应改变源列表");
  }

  @Test
  void appendHandlesNullSourceGracefully() {
    List<Object> result = StdList.append(null, 1);
    assertEquals(List.of(1), result);
  }

  @Test
  void mapAppliesFnToEachElement() {
    Fn1<Object, Object> doubleIt = x -> ((Integer) x) * 2;
    List<Object> result = StdList.map(List.of(1, 2, 3), doubleIt);
    assertEquals(List.of(2, 4, 6), result);
  }

  @Test
  void mapPreservesEmpty() {
    Fn1<Object, Object> id = x -> x;
    assertEquals(List.of(), StdList.map(List.of(), id));
    assertEquals(List.of(), StdList.map(null, id));
  }

  @Test
  void filterKeepsTruePredicateMatches() {
    Fn1<Object, Object> isEven = x -> ((Integer) x) % 2 == 0;
    List<Object> result = StdList.filter(List.of(1, 2, 3, 4), isEven);
    assertEquals(List.of(2, 4), result);
  }

  @Test
  void filterRejectsNonBoolPredicate() {
    Fn1<Object, Object> wrongType = x -> "not a bool";
    assertThrows(RuntimeException.class,
        () -> StdList.filter(List.of(1), wrongType));
  }

  @Test
  void mapRejectsNullFn() {
    assertThrows(NullPointerException.class,
        () -> StdList.map(List.of(1), null));
  }

  @Test
  void filterRejectsNullPredicate() {
    assertThrows(NullPointerException.class,
        () -> StdList.filter(List.of(1), null));
  }
}
