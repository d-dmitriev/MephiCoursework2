package home.work.observers;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Абстрактный класс для создания наблюдателей, которые могут быть отменены. Реализует интерфейсы
 * Observer и Disposable.
 *
 * @param <T> Тип элементов, которые будет обрабатывать наблюдатель.
 */
public abstract class DisposableObserver<T> implements Observer<T>, Disposable {
  /** Реализация механизма отмены подписки. */
  private final AtomicBoolean disposed = new AtomicBoolean(false);

  @Override
  public void dispose() {
    disposed.set(true);
  }

  @Override
  public boolean isDisposed() {
    return disposed.get();
  }
}
