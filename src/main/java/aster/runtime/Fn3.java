package aster.runtime;

@FunctionalInterface
public interface Fn3<A, B, C, R> {
  R apply(A a, B b, C c);
}

