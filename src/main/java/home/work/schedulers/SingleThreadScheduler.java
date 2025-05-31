package home.work.schedulers;

import home.work.observers.Scheduler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleThreadScheduler implements Scheduler {
  /** Планировщик единственного потока. */
  private final ExecutorService pool = Executors.newSingleThreadExecutor();

  @Override
  public void execute(Runnable task) {
    pool.execute(task);
  }

  @Override
  public void close() {
    pool.shutdown();
  }
}
