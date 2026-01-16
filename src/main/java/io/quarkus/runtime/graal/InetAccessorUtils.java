package io.quarkus.runtime.graal;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

final class InetAccessorUtils {
  private InetAccessorUtils() {
  }

  static Inet4Address resolveV4(String host) {
    InetAddress address = resolve(host);
    if (address instanceof Inet4Address inet4) {
      return inet4;
    }
    throw new IllegalStateException("无法解析 IPv4 地址: " + host);
  }

  static Inet6Address resolveV6(String host) {
    InetAddress address = resolve(host);
    if (address instanceof Inet6Address inet6) {
      return inet6;
    }
    throw new IllegalStateException("无法解析 IPv6 地址: " + host);
  }

  private static InetAddress resolve(String host) {
    try {
      return InetAddress.getByName(host);
    } catch (UnknownHostException ex) {
      throw new IllegalStateException("无法解析地址: " + host, ex);
    }
  }
}
