package aster.runtime;

/** Utility constructors for primitive-like values used by the emitter. */
public final class Primitives {
  private Primitives() {}

  /** Box a double as a {@link java.lang.Double}. */
  public static Double number(double d) { return Double.valueOf(d); }

  /** Box an int as a {@link java.lang.Integer}. */
  public static Integer integer(int i) { return Integer.valueOf(i); }

  /** Box a long as a {@link java.lang.Long}. */
  public static Long longNum(long l) { return Long.valueOf(l); }

  /** Box a boolean as a {@link java.lang.Boolean}. */
  public static Boolean bool(boolean b) { return Boolean.valueOf(b); }
}
