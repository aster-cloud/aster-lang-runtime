package aster.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StdResult Result/Ok/Err 辅助方法的最小基线测试。
 *
 * 覆盖 Map 表示（emitter 默认）和反射对象表示（手写代码集成）两条路径，
 * 保证 unwrap/mapOk/mapErr/构造器在两种表示间一致。
 */
class StdResultTest {

  @Test
  void okMapHasExpectedShape() {
    Map<Object, Object> ok = StdResult.okMap(42);
    assertEquals("Ok", ok.get("_type"));
    assertEquals(42, ok.get("value"));
  }

  @Test
  void errMapHasExpectedShape() {
    Map<Object, Object> err = StdResult.errMap("oops");
    assertEquals("Err", err.get("_type"));
    assertEquals("oops", err.get("value"));
  }

  @Test
  void unwrapReturnsOkValue() {
    Map<Object, Object> ok = StdResult.okMap("hello");
    assertEquals("hello", StdResult.unwrap(ok));
  }

  @Test
  void unwrapThrowsOnErr() {
    Map<Object, Object> err = StdResult.errMap("fail");
    assertThrows(RuntimeException.class, () -> StdResult.unwrap(err));
  }

  @Test
  void unwrapErrReturnsErrValue() {
    Map<Object, Object> err = StdResult.errMap("fail");
    assertEquals("fail", StdResult.unwrapErr(err));
  }

  @Test
  void unwrapErrThrowsOnOk() {
    Map<Object, Object> ok = StdResult.okMap(1);
    assertThrows(RuntimeException.class, () -> StdResult.unwrapErr(ok));
  }

  @Test
  void mapOkAppliesFnOnOkBranch() {
    Map<Object, Object> ok = StdResult.okMap(5);
    Fn1<Object, Object> doubleIt = x -> ((Integer) x) * 2;
    Object mapped = StdResult.mapOk(ok, doubleIt);
    assertEquals(10, StdResult.unwrap(mapped));
  }

  @Test
  void mapOkLeavesErrUnchanged() {
    Map<Object, Object> err = StdResult.errMap("nope");
    Fn1<Object, Object> doubleIt = x -> ((Integer) x) * 2;
    Object mapped = StdResult.mapOk(err, doubleIt);
    assertEquals("nope", StdResult.unwrapErr(mapped));
  }

  @Test
  void mapErrAppliesFnOnErrBranch() {
    Map<Object, Object> err = StdResult.errMap("bad");
    Fn1<Object, Object> upper = x -> ((String) x).toUpperCase();
    Object mapped = StdResult.mapErr(err, upper);
    assertEquals("BAD", StdResult.unwrapErr(mapped));
  }

  @Test
  void mapErrLeavesOkUnchanged() {
    Map<Object, Object> ok = StdResult.okMap(1);
    Fn1<Object, Object> upper = x -> ((String) x).toUpperCase();
    Object mapped = StdResult.mapErr(ok, upper);
    assertEquals(1, StdResult.unwrap(mapped));
  }

  @Test
  void okIntDelegatesToOkMap() {
    Map<Object, Object> ok = StdResult.okInt(7);
    assertEquals(7, ok.get("value"));
  }

  @Test
  void errTextDelegatesToErrMap() {
    Map<Object, Object> err = StdResult.errText("msg");
    assertEquals("msg", err.get("value"));
  }

  @Test
  void mapOkRejectsNonResult() {
    Fn1<Object, Object> id = x -> x;
    assertThrows(RuntimeException.class, () -> StdResult.mapOk("not a result", id));
  }

  @Test
  void mapOkRejectsNullFn() {
    Map<Object, Object> ok = StdResult.okMap(1);
    assertThrows(NullPointerException.class, () -> StdResult.mapOk(ok, null));
  }

  // ==================== 对象表示 (Ok/Err 密封类型) ====================
  // 这些用例验证 #4：用 instanceof Ok/Err 的模式匹配取代反射读字段，
  // 并修正 Err 字段名（error，而非 value）。

  @Test
  void unwrapReturnsOkObjectValue() {
    Result<String, String> ok = new Ok<>("hello");
    assertEquals("hello", StdResult.unwrap(ok));
  }

  @Test
  void unwrapThrowsOnErrObject() {
    Result<String, String> err = new Err<>("boom");
    assertThrows(RuntimeException.class, () -> StdResult.unwrap(err));
  }

  @Test
  void unwrapErrReturnsErrObjectError() {
    // 历史 bug：候选字段名为 ("value","error")，但 Err 声明的是 error；
    // 旧反射实现先匹配不到 value 才回退到 error。模式匹配直接读 err.error。
    Result<String, String> err = new Err<>("the-error");
    assertEquals("the-error", StdResult.unwrapErr(err));
  }

  @Test
  void unwrapErrThrowsOnOkObject() {
    Result<String, String> ok = new Ok<>("ok");
    assertThrows(RuntimeException.class, () -> StdResult.unwrapErr(ok));
  }

  @Test
  void mapOkOverOkObjectReturnsNewOk() {
    Result<Integer, String> ok = new Ok<>(5);
    Fn1<Object, Object> doubleIt = x -> ((Integer) x) * 2;
    Object mapped = StdResult.mapOk(ok, doubleIt);
    assertInstanceOf(Ok.class, mapped);
    assertEquals(10, StdResult.unwrap(mapped));
  }

  @Test
  void mapOkOverErrObjectIsUnchanged() {
    Result<Integer, String> err = new Err<>("nope");
    Fn1<Object, Object> doubleIt = x -> ((Integer) x) * 2;
    Object mapped = StdResult.mapOk(err, doubleIt);
    assertSame(err, mapped);
    assertEquals("nope", StdResult.unwrapErr(mapped));
  }

  @Test
  void mapErrOverErrObjectReturnsNewErr() {
    Result<Integer, String> err = new Err<>("bad");
    Fn1<Object, Object> upper = x -> ((String) x).toUpperCase();
    Object mapped = StdResult.mapErr(err, upper);
    assertInstanceOf(Err.class, mapped);
    assertEquals("BAD", StdResult.unwrapErr(mapped));
  }

  @Test
  void mapErrOverOkObjectIsUnchanged() {
    Result<Integer, String> ok = new Ok<>(7);
    Fn1<Object, Object> upper = x -> ((String) x).toUpperCase();
    Object mapped = StdResult.mapErr(ok, upper);
    assertSame(ok, mapped);
    assertEquals(7, StdResult.unwrap(mapped));
  }
}
