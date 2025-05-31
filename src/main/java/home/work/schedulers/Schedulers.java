package home.work.schedulers;

import home.work.observers.Scheduler;

public class Schedulers {
  /**
   * Возвращает планировщик, подходящий для операций ввода-вывода
   *
   * @return планировщик для операций ввода-вывода
   */
  public static Scheduler io() {
    return new IOThreadScheduler();
  }

  /**
   * Возвращает планировщик, подходящий для CPU-интенсивных задач
   *
   * @return планировщик для CPU-интенсивных задач
   */
  public static Scheduler computation() {
    return new ComputationScheduler();
  }

  /**
   * Возвращает планировщик с одним потоком
   *
   * @return планировщик с одним потоком
   */
  public static Scheduler single() {
    return new SingleThreadScheduler();
  }
}
