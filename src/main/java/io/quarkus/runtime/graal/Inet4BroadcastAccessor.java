package io.quarkus.runtime.graal;

import java.net.Inet4Address;

/**
 * 处理 {@code Inet.INET4_BROADCAST} 静态字段。
 */
public final class Inet4BroadcastAccessor {
  private static volatile Inet4Address value = InetAccessorUtils.resolveV4("255.255.255.255");

  private Inet4BroadcastAccessor() {
  }

  public static Inet4Address get() {
    return value;
  }

  public static void set(Inet4Address newValue) {
    value = newValue;
  }
}
