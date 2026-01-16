package aster.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Map 相关辅助方法，用于纯 Java 后端的 Core IR 测试。
 */
public final class StdMap {
  private StdMap() {}

  /** 创建新的可变 Map。 */
  public static Map<Object, Object> empty() {
    return new HashMap<>();
  }

  /** 返回附加键值后的新 Map，保持不可变语义。 */
  @SuppressWarnings("unchecked")
  public static Map<Object, Object> put(Object source, Object key, Object value) {
    Map<Object, Object> copy = new HashMap<>();
    if (source instanceof Map<?, ?> map) {
      copy.putAll((Map<Object, Object>) map);
    }
    copy.put(key, value);
    return copy;
  }
}
