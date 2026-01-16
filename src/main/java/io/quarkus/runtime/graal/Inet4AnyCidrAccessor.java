package io.quarkus.runtime.graal;

import io.smallrye.common.net.CidrAddress;

/**
 * 处理 {@code Inet.INET4_ANY_CIDR} 静态字段。
 * 使用延迟初始化避免在 GraalVM Native Image 构建时将 Inet4Address 对象放入 image heap。
 */
public final class Inet4AnyCidrAccessor {
  private static volatile CidrAddress value;

  private Inet4AnyCidrAccessor() {
  }

  public static CidrAddress get() {
    CidrAddress result = value;
    if (result == null) {
      synchronized (Inet4AnyCidrAccessor.class) {
        result = value;
        if (result == null) {
          value = result = CidrAddress.INET4_ANY_CIDR;
        }
      }
    }
    return result;
  }

  public static void set(CidrAddress newValue) {
    value = newValue;
  }
}
