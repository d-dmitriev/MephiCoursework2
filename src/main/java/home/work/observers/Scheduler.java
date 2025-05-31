package home.work.observers;

public interface Scheduler extends AutoCloseable {
  /**
   * Выполняет задачу в планировщике.
   *
   * @param task задача для выполнения
   */
  void execute(Runnable task);
}
