package home.work.schedulers;

import home.work.observers.Scheduler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ComputationScheduler implements Scheduler {
  /**
   * Планировщик для CPU-интенсивных задач. Использует пул потоков, равный количеству доступных
   * процессоров.
   */
  private final ExecutorService pool =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

  @Override
  public void execute(Runnable task) {
    pool.execute(task);
  }

  @Override
  public void close() {
    pool.shutdown();
  }
}
