package home.work;

import static org.junit.jupiter.api.Assertions.*;

import home.work.observers.Disposable;
import home.work.observers.DisposableObserver;
import home.work.observers.Observable;
import home.work.observers.Observer;
import home.work.schedulers.Schedulers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObservableTest {

  // === БАЗОВЫЕ ТЕСТЫ OBSERVABLE ===

  @Test
  void testCreateObservable() {
    Observable<Integer> observable =
        Observable.create(
            emitter -> {
              emitter.onNext(1);
              emitter.onComplete();
            });

    assertNotNull(observable);
  }

  @Test
  void testSubscribeAndGetItems() {
    AtomicInteger received = new AtomicInteger(0);
    AtomicBoolean completed = new AtomicBoolean(false);

    Observable.just(1, 2, 3)
        .subscribe(received::set, Throwable::printStackTrace, () -> completed.set(true));

    assertEquals(3, received.get());
    assertTrue(completed.get());
  }

  @Test
  void testOnErrorHandling() {
    AtomicBoolean errorReceived = new AtomicBoolean(false);

    Observable.create(emitter -> emitter.onError(new RuntimeException("Test error")))
        .subscribe(
            _ -> fail("onNext should not be called"),
            throwable -> {
              assertEquals("Test error", throwable.getMessage());
              errorReceived.set(true);
            },
            () -> fail("onComplete should not be called"));

    assertTrue(errorReceived.get());
  }

  @Test
  void testOnComplete() {
    AtomicBoolean completed = new AtomicBoolean(false);

    Observable.just(1).subscribe(_ -> {}, Throwable::printStackTrace, () -> completed.set(true));

    assertTrue(completed.get());
  }

  // === ТЕСТЫ ОПЕРАТОРОВ ===

  @Test
  void testMapOperator() {
    AtomicInteger result = new AtomicInteger();

    Observable.just(5).map(x -> x * x).subscribe(result::set, Throwable::printStackTrace);

    assertEquals(25, result.get());
  }

  @Test
  void testFilterOperator() {
    AtomicInteger count = new AtomicInteger(0);

    Observable.range(1, 5, i -> i + 1)
        .filter(x -> x % 2 == 0)
        .subscribe(_ -> count.incrementAndGet(), Throwable::printStackTrace);

    assertEquals(2, count.get());
  }

  @Test
  void testFlatMapOperator() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger count = new AtomicInteger(0);

    Observable.just(2, 3)
        .flatMap(x -> Observable.range(1, x, i -> i + 1))
        .subscribe(
            _ -> {
              count.incrementAndGet();
              latch.countDown();
            },
            Throwable::printStackTrace);

    latch.await();
    assertEquals(5, count.get()); // 1+2+1+2+3 (если flatMap сливает все эмиссии)
  }

  @Test
  void testRangeOperator() {
    AtomicInteger sum = new AtomicInteger(0);

    Observable.range(1, 5, i -> i + 1).subscribe(sum::addAndGet, Throwable::printStackTrace);

    assertEquals(15, sum.get());
  }

  @Test
  void testTakeOperator() {
    AtomicInteger count = new AtomicInteger(0);

    Observable.range(1, 10)
        .take(3)
        .subscribe(item -> count.incrementAndGet(), Throwable::printStackTrace);

    assertEquals(3, count.get());
  }

  @Test
  void testDistinctOperator() {
    AtomicInteger count = new AtomicInteger(0);

    Observable.just(1, 2, 2, 3, 1, 4, 4, 5)
        .distinct()
        .subscribe(item -> count.incrementAndGet(), Throwable::printStackTrace);

    assertEquals(5, count.get()); // 1,2,3,4,5
  }

  @Test
  void testDebounceOperator() throws InterruptedException {
    StringBuilder result = new StringBuilder();

    Observable.create(
            emitter -> {
              emitter.onNext("A");
              Thread.sleep(100);
              emitter.onNext("B");
              Thread.sleep(200);
              emitter.onNext("C");
              Thread.sleep(300);
              emitter.onNext("D");
              emitter.onComplete();
            })
        .debounce(150)
        .subscribe(result::append, Throwable::printStackTrace);

    Thread.sleep(1000); // Ждём завершения debounce

    // "A" не пройдет (B слишком быстро), "B" пройдет, "C" пройдет, "D" не пройдет
    assertTrue(result.toString().contains("B"));
    assertTrue(result.toString().contains("C"));
  }

  // === ТЕСТЫ SCHEDULERS ===

  @Test
  void testSubscribeOn() throws InterruptedException {
    AtomicBoolean isIOThread = new AtomicBoolean(false);

    Observable.just(1)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.single())
        .subscribe(
            _ -> isIOThread.set(!Thread.currentThread().getName().contains("main")),
            Throwable::printStackTrace);

    Thread.sleep(500); // Ждём выполнения асинхронного кода
    assertTrue(isIOThread.get());
  }

  @Test
  void testObserveOn() throws InterruptedException {
    String[] threadName = new String[1];

    Observable.just(1)
        .observeOn(Schedulers.computation())
        .subscribe(
            _ -> threadName[0] = Thread.currentThread().getName(), Throwable::printStackTrace);

    Thread.sleep(500);
    assertNotNull(threadName[0]);
    assertTrue(threadName[0].contains("ForkJoinPool") || threadName[0].contains("pool"));
  }

  @Test
  void testSchedulersMultithreading() throws InterruptedException {
    int[] callCount = {0};
    CountDownLatch latch = new CountDownLatch(10);

    Observable.range(1, 10, i -> i + 1)
        .subscribeOn(Schedulers.computation())
        .subscribe(
            item -> {
              System.out.println(
                  "Item " + item + " on thread: " + Thread.currentThread().getName());
              callCount[0]++;
              latch.countDown();
            },
            Throwable::printStackTrace);

    latch.await();
    assertEquals(10, callCount[0]);
  }

  // === ТЕСТЫ DISPOSABLE ===

  @Test
  void testDisposableStopsEmitting() throws InterruptedException {
    AtomicInteger count = new AtomicInteger(0);
    AtomicBoolean completed = new AtomicBoolean(false);

    Observer<Integer> d =
        new DisposableObserver<>() {
          @Override
          public void onNext(Integer item) {
            count.incrementAndGet();
            if (count.get() == 5) {
              dispose(); // Остановить после 5-го элемента
            }
          }

          @Override
          public void onError(Throwable t) {
            fail("onError should not be called");
          }

          @Override
          public void onComplete() {
            completed.set(true);
          }
        };

    Observable.range(1, 100).subscribe(d);

    Thread.sleep(100); // Дать время на выполнение

    assertTrue(count.get() <= 6); // 5 onNext + возможно 1 после dispose
    assertFalse(completed.get());
  }

  @Test
  void testDisposablePreventsOnCompleteAndOnError() throws InterruptedException {
    AtomicBoolean completed = new AtomicBoolean(false);
    AtomicBoolean error = new AtomicBoolean(false);

    Observer<Object> disposable =
        new DisposableObserver<>() {
          @Override
          public void onNext(Object item) {
            dispose();
          }

          @Override
          public void onError(Throwable t) {
            if (isDisposed()) return;
            error.set(true);
          }

          @Override
          public void onComplete() {
            if (isDisposed()) return;
            completed.set(true);
          }
        };

    Observable.create(
            emitter -> {
              emitter.onNext(1);
              emitter.onComplete();
              emitter.onError(new RuntimeException("Should not be called"));
            })
        .subscribe(disposable);

    Thread.sleep(100);

    assertFalse(completed.get());
    assertFalse(error.get());
    assertTrue(((Disposable) disposable).isDisposed());
  }
}
