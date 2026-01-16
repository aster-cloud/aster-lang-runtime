package aster.runtime;

@FunctionalInterface
public interface Fn4<A, B, C, D, R> {
  R apply(A a, B b, C c, D d);
}

