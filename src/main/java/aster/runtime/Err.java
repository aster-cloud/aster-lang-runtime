package aster.runtime;

public final class Err<T,E> implements Result<T,E> {
  public final E error;
  public Err(E e){ this.error = e; }
}

