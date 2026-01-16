package io.quarkus.runtime.graal;

import java.net.Inet6Address;

/**
 * 处理 {@code Inet.INET6_LOOPBACK} 静态字段。
 */
public final class Inet6LoopbackAccessor {
  private static volatile Inet6Address value = InetAccessorUtils.resolveV6("::1");

  private Inet6LoopbackAccessor() {
  }

  public static Inet6Address get() {
    return value;
  }

  public static void set(Inet6Address newValue) {
    value = newValue;
  }
}
