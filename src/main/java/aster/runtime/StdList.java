package aster.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * List 相关辅助方法，供字节码后端复用。
 */
public final class StdList {
  private StdList() {}

  /** 创建新的可变列表。 */
  public static List<Object> empty() {
    return new ArrayList<>();
  }

  /** 返回附加元素后的新列表。 */
  public static List<Object> append(List<?> source, Object value) {
    List<Object> copy = new ArrayList<>();
    if (source != null) {
      copy.addAll(source);
    }
    copy.add(value);
    return copy;
  }

  /** 对列表执行 map，返回映射后的新列表。 */
  @SuppressWarnings("unchecked")
  public static List<Object> map(List<?> source, Object fn) {
    Fn1<Object, Object> mapper = (Fn1<Object, Object>) Objects.requireNonNull(fn, "fn");
    List<Object> result = new ArrayList<>(source != null ? source.size() : 0);
    if (source != null) {
      for (Object element : source) {
        result.add(mapper.apply(element));
      }
    }
    return result;
  }

  /** 根据谓词过滤列表元素，返回保留后的新列表。 */
  @SuppressWarnings("unchecked")
  public static List<Object> filter(List<?> source, Object fn) {
    Fn1<Object, Object> predicate = (Fn1<Object, Object>) Objects.requireNonNull(fn, "fn");
    List<Object> result = new ArrayList<>(source != null ? source.size() : 0);
    if (source != null) {
      for (Object element : source) {
        Object decision = predicate.apply(element);
        if (!(decision instanceof Boolean keep)) {
          throw new RuntimeException("List.filter: predicate must return Bool");
        }
        if (keep) {
          result.add(element);
        }
      }
    }
    return result;
  }

  /** 以归约函数遍历列表，返回最终累积值。 */
  @SuppressWarnings("unchecked")
  public static Object reduce(List<?> source, Object initial, Object fn) {
    Fn2<Object, Object, Object> reducer = (Fn2<Object, Object, Object>) Objects.requireNonNull(fn, "fn");
    Object acc = initial;
    if (source != null) {
      for (Object element : source) {
        acc = reducer.apply(acc, element);
      }
    }
    return acc;
  }
}
