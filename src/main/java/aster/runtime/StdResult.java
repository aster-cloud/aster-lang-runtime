package aster.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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

    if (isErrObject(result) || isErrMap(result)) {
      return result;
    }

    if (isOkObject(result)) {
      Object okValue = readField(result, "value");
      Object mapped = mapper.apply(okValue);
      return rebuildVariant(result, mapped, /*isOk=*/true);
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

    if (isOkObject(result) || isOkMap(result)) {
      return result;
    }

    if (isErrObject(result)) {
      Object errValue = readField(result, "value", "error");
      Object mapped = mapper.apply(errValue);
      return rebuildVariant(result, mapped, /*isOk=*/false);
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
    if (isOkObject(result)) {
      return readField(result, "value", "value", "val");
    }
    if (isOkMap(result)) {
      return readMapPayload((Map<?, ?>) result);
    }
    throw new RuntimeException("Result.unwrap: called on Err");
  }

  /** 解包 Err 值。*/
  public static Object unwrapErr(Object result) {
    if (isErrObject(result)) {
      return readField(result, "value", "error");
    }
    if (isErrMap(result)) {
      return readMapPayload((Map<?, ?>) result);
    }
    throw new RuntimeException("Result.unwrapErr: called on Ok");
  }

  private static boolean isOkObject(Object value) {
    return value != null && "Ok".equals(value.getClass().getSimpleName());
  }

  private static boolean isErrObject(Object value) {
    return value != null && "Err".equals(value.getClass().getSimpleName());
  }

  private static boolean isOkMap(Object value) {
    return value instanceof Map<?, ?> m && "Ok".equals(m.get("_type"));
  }

  private static boolean isErrMap(Object value) {
    return value instanceof Map<?, ?> m && "Err".equals(m.get("_type"));
  }

  private static Object readField(Object target, String... candidates) {
    Class<?> cls = target.getClass();
    for (String name : candidates) {
      try {
        Field f = cls.getField(name);
        return f.get(target);
      } catch (NoSuchFieldException ignored) {
        // 尝试下一个候选字段名
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Result helper: failed to access field '" + name + "'");
      }
    }
    throw new RuntimeException("Result helper: missing value field on " + cls.getName());
  }

  private static Object readMapPayload(Map<?, ?> map) {
    if (map.containsKey("value")) return map.get("value");
    if (map.containsKey("error")) return map.get("error");
    return null;
  }

  private static Object rebuildVariant(Object original, Object newValue, boolean isOk) {
    Class<?> cls = original.getClass();

    try {
      Constructor<?> ctor = cls.getConstructor(Object.class);
      return ctor.newInstance(newValue);
    } catch (ReflectiveOperationException ignored) {
      // 回退到默认实现
    }

    return isOk ? new Ok<>(newValue) : new Err<>(newValue);
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }
}
