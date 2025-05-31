package home.work.observers;

public interface Observer<T> {
  /**
   * Метод вызывается для передачи следующего элемента наблюдателю.
   *
   * @param item элемент, который нужно передать
   */
  void onNext(T item);

  /**
   * Метод вызывается при возникновении ошибки в процессе наблюдения.
   *
   * @param t ошибка, произошедшая в процессе
   */
  void onError(Throwable t);

  /** Метод вызывается при завершении наблюдения. */
  void onComplete();
}
