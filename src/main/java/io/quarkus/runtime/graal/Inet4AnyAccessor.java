package io.quarkus.runtime.graal;

import java.net.Inet4Address;

/**
 * 提供对 {@code Inet.INET4_ANY} 的可控访问，避免 GraalVM 缺失 setter 时注入失败。
 * 该类在构建时会覆盖 Quarkus 依赖中的同名实现。
 */
public final class Inet4AnyAccessor {
  private static volatile Inet4Address value = InetAccessorUtils.resolveV4("0.0.0.0");

  private Inet4AnyAccessor() {
  }

  public static Inet4Address get() {
    return value;
  }

  public static void set(Inet4Address newValue) {
    value = newValue;
  }
}
