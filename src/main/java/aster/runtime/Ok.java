package aster.runtime;

public final class Ok<T,E> implements Result<T,E> {
  public final T value;
  public Ok(T v){ this.value = v; }
}

