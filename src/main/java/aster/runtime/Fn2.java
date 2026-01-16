package aster.runtime;

@FunctionalInterface
public interface Fn2<A, B, R> {
  R apply(A a, B b);
}

