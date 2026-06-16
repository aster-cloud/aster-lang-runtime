package aster.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result 相关辅助方法，纯 Java 后端专用。
 */
public final class StdResult {
  private StdResult() {}

  /** 对 Ok 分支应用映射函数，Err 分支保持不变。 */
  @SuppressWarnings("unchecked")
  public static Object mapOk(Object result, Object fn) {
    Fn1<Object, Object> mapper = (Fn1<Object, Object>) Objects.requireNonNull(fn, "fn");

    if (result instanceof Err<?, ?> || isErrMap(result)) {
      return result;
    }

    if (result instanceof Ok<?, ?> ok) {
      Object mapped = mapper.apply(ok.value);
      return new Ok<>(mapped);
    }

    if (result instanceof Map<?, ?> m && "Ok".equals(m.get("_type"))) {
      Object mapped = mapper.apply(readMapPayload(m));
      Map<String, Object> copy = new HashMap<>();
      copy.put("_type", "Ok");
      copy.put("value", mapped);
      return copy;
    }

    throw new RuntimeException("Result.mapOk: expected Result (Ok or Err), got " + typeName(result));
  }

  /** 对 Err 分支应用映射函数，Ok 分支保持不变。 */
  @SuppressWarnings("unchecked")
  public static Object mapErr(Object result, Object fn) {
    Fn1<Object, Object> mapper = (Fn1<Object, Object>) Objects.requireNonNull(fn, "fn");

    if (result instanceof Ok<?, ?> || isOkMap(result)) {
      return result;
    }

    if (result instanceof Err<?, ?> err) {
      Object mapped = mapper.apply(err.error);
      return new Err<>(mapped);
    }

    if (result instanceof Map<?, ?> m && "Err".equals(m.get("_type"))) {
      Object mapped = mapper.apply(readMapPayload(m));
      Map<String, Object> copy = new HashMap<>();
      copy.put("_type", "Err");
      copy.put("value", mapped);
      return copy;
    }

    throw new RuntimeException("Result.mapErr: expected Result (Ok or Err), got " + typeName(result));
  }

  /** 构造 Ok 的 Map 表示。*/
  public static Map<Object, Object> okMap(Object value) {
    Map<Object, Object> map = new HashMap<>();
    map.put("_type", "Ok");
    map.put("value", value);
    return map;
  }

  public static Map<Object, Object> okInt(int value) {
    return okMap(value);
  }

  /** 构造 Err 的 Map 表示。*/
  public static Map<Object, Object> errMap(Object value) {
    Map<Object, Object> map = new HashMap<>();
    map.put("_type", "Err");
    map.put("value", value);
    return map;
  }

  public static Map<Object, Object> errText(String value) {
    return errMap(value);
  }

  /** 解包 Ok 值。*/
  public static Object unwrap(Object result) {
    if (result instanceof Ok<?, ?> ok) {
      return ok.value;
    }
    if (isOkMap(result)) {
      return readMapPayload((Map<?, ?>) result);
    }
    throw new RuntimeException("Result.unwrap: called on Err");
  }

  /** 解包 Err 值。*/
  public static Object unwrapErr(Object result) {
    if (result instanceof Err<?, ?> err) {
      return err.error;
    }
    if (isErrMap(result)) {
      return readMapPayload((Map<?, ?>) result);
    }
    throw new RuntimeException("Result.unwrapErr: called on Ok");
  }

  private static boolean isOkMap(Object value) {
    return value instanceof Map<?, ?> m && "Ok".equals(m.get("_type"));
  }

  private static boolean isErrMap(Object value) {
    return value instanceof Map<?, ?> m && "Err".equals(m.get("_type"));
  }

  private static Object readMapPayload(Map<?, ?> map) {
    if (map.containsKey("value")) return map.get("value");
    if (map.containsKey("error")) return map.get("error");
    return null;
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }
}
