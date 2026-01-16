package aster.runtime;

@FunctionalInterface
public interface Fn1<T, R> {
  R apply(T a);
}

