package home.work.schedulers;

import home.work.observers.Scheduler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IOThreadScheduler implements Scheduler {
  /** Планировщик для операций ввода-вывода (I/O) с использованием пула потоков. */
  private final ExecutorService pool = Executors.newCachedThreadPool();

  @Override
  public void execute(Runnable task) {
    pool.execute(task);
  }

  @Override
  public void close() {
    pool.shutdown();
  }
}
