package io.quarkus.runtime.graal;

import java.net.Inet4Address;

/**
 * 处理 {@code Inet.INET4_LOOPBACK} 静态字段。
 */
public final class Inet4LoopbackAccessor {
  private static volatile Inet4Address value = InetAccessorUtils.resolveV4("127.0.0.1");

  private Inet4LoopbackAccessor() {
  }

  public static Inet4Address get() {
    return value;
  }

  public static void set(Inet4Address newValue) {
    value = newValue;
  }
}
