package aster.runtime;

public final class Interop {
  private Interop() {}

  public static String pick(int x) { return "int:" + x; }
  public static String pick(Object x) { return "obj:" + String.valueOf(x); }
  public static String pick(boolean x) { return "bool:" + x; }
  public static String pick(String x) { return "str:" + x; }
  public static String pick(long x) { return "long:" + x; }
  public static String pick(double x) { return "double:" + x; }

  public static String sum(int a, int b) { return String.valueOf(a + b); }
  public static String sum(long a, long b) { return String.valueOf(a + b); }
  public static String sum(double a, double b) { return String.valueOf(a + b); }
}
