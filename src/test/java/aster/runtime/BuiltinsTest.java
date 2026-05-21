package aster.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builtins 操作符函数值的最小基线测试。
 *
 * 这些 Fn2 是 emitter 在把操作符作为一等函数值（lambda capture、Fn 参数）
 * 时直接引用的句柄。任何语义回归（比较反向、类型放宽）都会让生成代码 silently 给出错误结果。
 */
class BuiltinsTest {

  @Test
  @SuppressWarnings("unchecked")
  void equalsReturnsTrueForEqualValues() {
    assertEquals(Boolean.TRUE, Builtins.EQUALS.apply(1, 1));
    assertEquals(Boolean.TRUE, Builtins.EQUALS.apply("a", "a"));
    assertEquals(Boolean.TRUE, Builtins.EQUALS.apply(null, null));
  }

  @Test
  @SuppressWarnings("unchecked")
  void equalsReturnsFalseForUnequalValues() {
    assertEquals(Boolean.FALSE, Builtins.EQUALS.apply(1, 2));
    assertEquals(Boolean.FALSE, Builtins.EQUALS.apply("a", "b"));
    assertEquals(Boolean.FALSE, Builtins.EQUALS.apply(null, 1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void notEqualsIsLogicalInverseOfEquals() {
    assertEquals(Boolean.FALSE, Builtins.NOT_EQUALS.apply(1, 1));
    assertEquals(Boolean.TRUE, Builtins.NOT_EQUALS.apply(1, 2));
  }

  @Test
  @SuppressWarnings("unchecked")
  void lessThanWorksForIntegers() {
    assertEquals(Boolean.TRUE, Builtins.LESS_THAN.apply(1, 2));
    assertEquals(Boolean.FALSE, Builtins.LESS_THAN.apply(2, 1));
    assertEquals(Boolean.FALSE, Builtins.LESS_THAN.apply(1, 1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void lessThanWorksForLongs() {
    assertEquals(Boolean.TRUE, Builtins.LESS_THAN.apply(1L, 2L));
    assertEquals(Boolean.FALSE, Builtins.LESS_THAN.apply(2L, 1L));
  }

  @Test
  @SuppressWarnings("unchecked")
  void lessThanWorksForDoubles() {
    assertEquals(Boolean.TRUE, Builtins.LESS_THAN.apply(1.0, 2.0));
    assertEquals(Boolean.FALSE, Builtins.LESS_THAN.apply(2.0, 1.0));
  }

  @Test
  @SuppressWarnings("unchecked")
  void lessThanRejectsMixedTypes() {
    // 跨类型比较没有定义的语义；emitter 必须先对齐类型再调用。
    // 若放宽此约束会让生成代码与类型检查器期望脱节。
    assertThrows(IllegalArgumentException.class,
        () -> Builtins.LESS_THAN.apply(1, 1L));
    assertThrows(IllegalArgumentException.class,
        () -> Builtins.LESS_THAN.apply(1, 1.0));
  }

  @Test
  @SuppressWarnings("unchecked")
  void greaterThanWorksAcrossNumericTypes() {
    assertEquals(Boolean.TRUE, Builtins.GREATER_THAN.apply(2, 1));
    assertEquals(Boolean.TRUE, Builtins.GREATER_THAN.apply(2L, 1L));
    assertEquals(Boolean.TRUE, Builtins.GREATER_THAN.apply(2.0, 1.0));
    assertEquals(Boolean.FALSE, Builtins.GREATER_THAN.apply(1, 2));
  }

  @Test
  @SuppressWarnings("unchecked")
  void lessThanOrEqualHandlesEqualCase() {
    assertEquals(Boolean.TRUE, Builtins.LESS_THAN_OR_EQUAL.apply(1, 1));
    assertEquals(Boolean.TRUE, Builtins.LESS_THAN_OR_EQUAL.apply(1, 2));
    assertEquals(Boolean.FALSE, Builtins.LESS_THAN_OR_EQUAL.apply(2, 1));
  }
}
