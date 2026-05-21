package aster.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Primitives 装箱辅助方法的最小基线测试。
 *
 * 验证后端代码生成器期望的装箱契约（返回非 null、类型严格、值正确）。
 * 任何回归都会破坏 emitter 生成的字节码语义，因此即使代码简单也必须有测试。
 */
class PrimitivesTest {

  @Test
  void numberBoxesDoubleStrictly() {
    Double d = Primitives.number(3.14);
    assertNotNull(d);
    assertEquals(3.14, d);
    assertSame(Double.class, d.getClass());
  }

  @Test
  void integerBoxesIntStrictly() {
    Integer i = Primitives.integer(42);
    assertNotNull(i);
    assertEquals(42, i.intValue());
    assertSame(Integer.class, i.getClass());
  }

  @Test
  void longNumBoxesLongStrictly() {
    Long l = Primitives.longNum(9_999_999_999L);
    assertNotNull(l);
    assertEquals(9_999_999_999L, l.longValue());
    assertSame(Long.class, l.getClass());
  }

  @Test
  void boolBoxesBooleanStrictly() {
    Boolean t = Primitives.bool(true);
    Boolean f = Primitives.bool(false);
    assertEquals(Boolean.TRUE, t);
    assertEquals(Boolean.FALSE, f);
    assertSame(Boolean.class, t.getClass());
  }

  @Test
  void integerExtremesPreserveValue() {
    assertEquals(Integer.MIN_VALUE, Primitives.integer(Integer.MIN_VALUE).intValue());
    assertEquals(Integer.MAX_VALUE, Primitives.integer(Integer.MAX_VALUE).intValue());
  }

  @Test
  void longExtremesPreserveValue() {
    assertEquals(Long.MIN_VALUE, Primitives.longNum(Long.MIN_VALUE).longValue());
    assertEquals(Long.MAX_VALUE, Primitives.longNum(Long.MAX_VALUE).longValue());
  }
}
