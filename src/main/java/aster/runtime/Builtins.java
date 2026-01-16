package aster.runtime;

import java.util.Objects;

/**
 * Built-in function objects for operators and common functions.
 * These are used when operators are passed as function values.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class Builtins {
  private Builtins() {}

  /** Equality operator: = */
  public static final Fn2 EQUALS = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      return Objects.equals(a, b);
    }
  };

  /** Not-equals operator: != */
  public static final Fn2 NOT_EQUALS = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      return !Objects.equals(a, b);
    }
  };

  /** Less-than operator: < */
  public static final Fn2 LESS_THAN = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) < ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) < ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) < ((Double) b);
      }
      throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
    }
  };

  /** Less-than-or-equal operator: <= */
  public static final Fn2 LESS_THAN_OR_EQUAL = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) <= ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) <= ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) <= ((Double) b);
      }
      throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
    }
  };

  /** Greater-than operator: > */
  public static final Fn2 GREATER_THAN = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) > ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) > ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) > ((Double) b);
      }
      throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
    }
  };

  /** Greater-than-or-equal operator: >= */
  public static final Fn2 GREATER_THAN_OR_EQUAL = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) >= ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) >= ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) >= ((Double) b);
      }
      throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
    }
  };

  /** Addition operator: + */
  public static final Fn2 ADD = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) + ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) + ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) + ((Double) b);
      }
      if (a instanceof String || b instanceof String) {
        return String.valueOf(a) + String.valueOf(b);
      }
      throw new IllegalArgumentException("Cannot add " + a + " and " + b);
    }
  };

  /** Subtraction operator: - */
  public static final Fn2 SUBTRACT = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) - ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) - ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) - ((Double) b);
      }
      throw new IllegalArgumentException("Cannot subtract " + a + " and " + b);
    }
  };

  /** Multiplication operator: * */
  public static final Fn2 MULTIPLY = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) * ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) * ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) * ((Double) b);
      }
      throw new IllegalArgumentException("Cannot multiply " + a + " and " + b);
    }
  };

  /** Division operator: / */
  public static final Fn2 DIVIDE = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) / ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) / ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) / ((Double) b);
      }
      throw new IllegalArgumentException("Cannot divide " + a + " and " + b);
    }
  };

  /** Modulo operator: % */
  public static final Fn2 MODULO = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Integer && b instanceof Integer) {
        return ((Integer) a) % ((Integer) b);
      }
      if (a instanceof Long && b instanceof Long) {
        return ((Long) a) % ((Long) b);
      }
      if (a instanceof Double && b instanceof Double) {
        return ((Double) a) % ((Double) b);
      }
      throw new IllegalArgumentException("Cannot modulo " + a + " and " + b);
    }
  };

  /** Logical AND operator: and */
  public static final Fn2 AND = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Boolean && b instanceof Boolean) {
        return ((Boolean) a) && ((Boolean) b);
      }
      throw new IllegalArgumentException("Cannot AND " + a + " and " + b);
    }
  };

  /** Logical OR operator: or */
  public static final Fn2 OR = new Fn2() {
    @Override
    public Object apply(Object a, Object b) {
      if (a instanceof Boolean && b instanceof Boolean) {
        return ((Boolean) a) || ((Boolean) b);
      }
      throw new IllegalArgumentException("Cannot OR " + a + " and " + b);
    }
  };

  /** Logical NOT operator: not */
  public static final Fn1 NOT = new Fn1() {
    @Override
    public Object apply(Object a) {
      if (a instanceof Boolean) {
        return !((Boolean) a);
      }
      throw new IllegalArgumentException("Cannot NOT " + a);
    }
  };
}
