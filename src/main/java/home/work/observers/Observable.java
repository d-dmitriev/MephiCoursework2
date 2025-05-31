package home.work.observers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Основной класс реактивного потока, представляющий собой источник данных, на который можно
 * подписаться для получения событий.
 *
 * @param <T> тип элементов, которые эмитит Observable
 */
public abstract class Observable<T> {
  /**
   * Метод, который вызывается при подписке на Observable. Реализуется в подклассах для определения
   * логики эмиссии элементов.
   *
   * @param observer наблюдатель, который будет получать события
   */
  public abstract void subscribeActual(Observer<T> observer);

  /**
   * Подписка на Observable. Возвращает Disposable для возможности прерывания.
   *
   * @param observer наблюдатель, который будет получать события
   * @return Disposable для управления подпиской
   */
  public Disposable subscribe(Observer<T> observer) {
    if (observer instanceof Disposable) {
      subscribeActual(observer);
      return (Disposable) observer;
    } else {
      DisposableObserver<T> disposableObserver =
          new DisposableObserver<>() {
            @Override
            public void onNext(T item) {
              if (isDisposed()) return;
              observer.onNext(item);
            }

            @Override
            public void onError(Throwable t) {
              if (isDisposed()) return;
              observer.onError(t);
            }

            @Override
            public void onComplete() {
              if (isDisposed()) return;
              observer.onComplete();
            }
          };
      subscribeActual(disposableObserver);
      return disposableObserver;
    }
  }

  /**
   * Подписка на Observable с использованием Consumer для обработки событий. Возвращает Disposable
   * для возможности прерывания.
   *
   * @param onNext обработчик для элементов
   * @param onError обработчик для ошибок
   * @param onComplete обработчик для завершения
   * @return Disposable для управления подпиской
   */
  public Disposable subscribe(
      Consumer<T> onNext, Consumer<Throwable> onError, Runnable onComplete) {
    DisposableObserver<T> observer =
        new DisposableObserver<>() {
          @Override
          public void onNext(T item) {
            if (!isDisposed()) onNext.accept(item);
          }

          @Override
          public void onError(Throwable t) {
            if (!isDisposed()) onError.accept(t);
          }

          @Override
          public void onComplete() {
            if (!isDisposed()) onComplete.run();
          }
        };
    subscribeActual(observer);
    return observer;
  }

  /**
   * Подписка на Observable с обработчиками для элементов и ошибок, без обработчика завершения.
   *
   * @param onNext обработчик для элементов
   * @param onError обработчик для ошибок
   * @return Disposable для управления подпиской
   */
  public Disposable subscribe(Consumer<T> onNext, Consumer<Throwable> onError) {
    return subscribe(onNext, onError, () -> {});
  }

  /**
   * Подписка на Observable с обработчиком для элементов, без обработки ошибок и завершения.
   *
   * @param onNext обработчик для элементов
   * @return Disposable для управления подпиской
   */
  public Disposable subscribe(Consumer<T> onNext) {
    return subscribe(onNext, Throwable::printStackTrace, () -> {});
  }

  /**
   * Подписка выполняется в указанном планировщике.
   *
   * @param scheduler планировщик для вызова source.subscribe
   * @return новый Observable, подписка которого отложена на scheduler
   */
  public Observable<T> subscribeOn(Scheduler scheduler) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        scheduler.execute(() -> Observable.this.subscribe(observer));
      }
    };
  }

  /**
   * Эмиссия onNext/onError/onComplete происходит в указанном планировщике.
   *
   * @param scheduler планировщик для обработки событий
   * @return новый Observable, события которого переключаются на scheduler
   */
  public Observable<T> observeOn(Scheduler scheduler) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            new Observer<>() {
              @Override
              public void onNext(T item) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                scheduler.execute(() -> observer.onNext(item));
              }

              @Override
              public void onError(Throwable t) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                scheduler.execute(() -> observer.onError(t));
              }

              @Override
              public void onComplete() {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                scheduler.execute(observer::onComplete);
              }
            });
      }
    };
  }

  /**
   * Создаёт Observable из ObservableOnSubscribe.
   *
   * @param source источник, который будет вызываться при подписке
   * @param <T> тип элементов
   * @return новый Observable
   */
  public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        try {
          source.subscribe(observer);
        } catch (Exception e) {
          observer.onError(e);
        }
      }
    };
  }

  /**
   * Создаёт Observable, который эмитит один элемент и завершает поток.
   *
   * @param item элемент для эмиссии
   * @param <T> тип элемента
   * @return новый Observable, эмитирующий один элемент
   */
  public static <T> Observable<T> just(T item) {
    return create(
        observer -> {
          try {
            observer.onNext(item);
            observer.onComplete();
          } catch (Exception e) {
            observer.onError(e);
          }
        });
  }

  /**
   * Создаёт Observable, который эмитит переданные элементы и сразу завершает поток.
   *
   * @param items элементы для эмиссии
   * @param <T> тип элементов
   * @return новый RxObservable
   */
  @SafeVarargs
  public static <T> Observable<T> just(T... items) {
    return create(
        observer -> {
          Arrays.stream(items)
              .forEach(
                  item -> {
                    try {
                      observer.onNext(item);
                    } catch (Exception e) {
                      observer.onError(e);
                    }
                  });
          observer.onComplete();
        });
  }

  /**
   * Создаёт Observable, который эмитит переданные элементы и сразу завершает поток.
   *
   * @param mapper элементы для эмиссии
   * @param <R> тип элементов
   * @return новый Observable, эмитирующий переданные элементы
   */
  public <R> Observable<R> map(Function<T, R> mapper) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<R> observer) {
        Observable.this.subscribe(
            item -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              try {
                observer.onNext(mapper.apply(item));
              } catch (Exception e) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(e);
              }
            },
            t -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onError(t);
            },
            () -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onComplete();
            });
      }
    };
  }

  /**
   * Пропускает элементы, которые не удовлетворяют предикату.
   *
   * @param predicate предикат для фильтрации элементов
   * @return новый Observable, который эмитит только элементы, удовлетворяющие предикату
   */
  public Observable<T> filter(Predicate<T> predicate) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            item -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              if (predicate.test(item)) {
                observer.onNext(item);
              }
            },
            t -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onError(t);
            },
            () -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onComplete();
            });
      }
    };
  }

  /**
   * Преобразует элементы Observable в другой Observable, используя функцию преобразования.
   *
   * @param mapper функция, которая преобразует элементы
   * @param <R> тип элементов нового Observable
   * @return новый Observable, который эмитит элементы, преобразованные функцией mapper
   */
  public <R> Observable<R> flatMap(Function<T, Observable<R>> mapper) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<R> observer) {
        Observable.this.subscribe(
            item -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              try {
                mapper
                    .apply(item)
                    .subscribe(
                        x -> {
                          if (observer instanceof Disposable
                              && ((Disposable) observer).isDisposed()) return;
                          observer.onNext(x);
                        },
                        t -> {
                          if (observer instanceof Disposable
                              && ((Disposable) observer).isDisposed()) return;
                          observer.onError(t);
                        });
              } catch (Exception e) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(e);
              }
            },
            t -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onError(t);
            },
            () -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onComplete();
            });
      }
    };
  }

  /**
   * Создаёт Observable, который эмитит последовательность значений, начиная с указанного значения.
   *
   * @param start начальное значение
   * @param count количество элементов для эмиссии
   * @param nextValue функция для получения следующего значения из текущего
   * @param <T> тип элементов
   * @return новый Observable, эмитирующий последовательность значений
   */
  public static <T> Observable<T> range(T start, int count, Function<T, T> nextValue) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        T value = start;
        for (int i = 0; i < count; i++) {
          if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) {
            return;
          }
          try {
            observer.onNext(value);
            value = nextValue.apply(value);
          } catch (Exception e) {
            observer.onError(e);
            return;
          }
        }
        observer.onComplete();
      }
    };
  }

  /**
   * Создаёт Observable, который эмитит последовательность целых чисел, начиная с указанного
   * значения.
   *
   * @param start начальное значение
   * @param count количество элементов для эмиссии
   * @return новый Observable, эмитирующий последовательность целых чисел
   */
  public static Observable<Integer> range(int start, int count) {
    return range(start, count, i -> i + 1);
  }

  /**
   * Возвращает Observable, который эмитит только первые n элементов.
   *
   * @param count максимальное количество элементов
   * @return новый Observable с ограниченным количеством элементов
   */
  public Observable<T> take(int count) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            new Observer<>() {
              int remaining = count;

              @Override
              public void onNext(T item) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                if (remaining > 0) {
                  observer.onNext(item);
                  remaining--;
                }
                if (remaining == 0) {
                  observer.onComplete();
                }
              }

              @Override
              public void onError(Throwable t) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(t);
              }

              @Override
              public void onComplete() {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onComplete();
              }
            });
      }
    };
  }

  /**
   * Пропускает элементы, которые следуют быстрее, чем указанный таймаут.
   *
   * @param timeout время задержки в миллисекундах
   * @return новый Observable с отфильтрованными элементами
   */
  public Observable<T> debounce(long timeout) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            new Observer<>() {
              private volatile TimerTask lastTask;
              private final Timer timer = new Timer(true);

              @Override
              public void onNext(T item) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                if (lastTask != null) {
                  lastTask.cancel();
                }

                lastTask =
                    new TimerTask() {
                      @Override
                      public void run() {
                        if (observer instanceof Disposable && ((Disposable) observer).isDisposed())
                          return;
                        observer.onNext(item);
                      }
                    };

                timer.schedule(lastTask, timeout);
              }

              @Override
              public void onError(Throwable t) {
                timer.cancel();
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(t);
              }

              @Override
              public void onComplete() {
                timer.cancel();
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onComplete();
              }
            });
      }
    };
  }

  /**
   * Возвращает Observable, который эмитит только уникальные элементы.
   *
   * @return новый Observable с уникальными элементами
   */
  public Observable<T> distinct() {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            new Observer<>() {
              final Set<T> seen = new HashSet<>();

              @Override
              public void onNext(T item) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                if (seen.add(item)) {
                  observer.onNext(item);
                }
              }

              @Override
              public void onError(Throwable t) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(t);
              }

              @Override
              public void onComplete() {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onComplete();
              }
            });
      }
    };
  }

  /**
   * При возникновении ошибки эмитит указанное значение и завершает поток.
   *
   * @param value значение, которое будет эмитировано при ошибке
   * @return новый Observable с обработкой ошибок
   */
  public Observable<T> onErrorReturn(T value) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            item -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onNext(item);
            },
            _ -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onNext(value);
              observer.onComplete();
            },
            () -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onComplete();
            });
      }
    };
  }

  /**
   * При возникновении ошибки эмитит значение, полученное из функции, и завершает поток.
   *
   * @param valueSupplier функция, возвращающая значение при ошибке
   * @return новый Observable с обработкой ошибок
   */
  public Observable<T> onErrorReturn(Function<Throwable, T> valueSupplier) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            item -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onNext(item);
            },
            throwable -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              try {
                observer.onNext(valueSupplier.apply(throwable));
                observer.onComplete();
              } catch (Exception e) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(e);
              }
            },
            () -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onComplete();
            });
      }
    };
  }

  /**
   * При возникновении ошибки продолжает работу с указанного Observable.
   *
   * @param resumeFunction функция, возвращающая Observable для продолжения
   * @return новый Observable с обработкой ошибок
   */
  public Observable<T> onErrorResumeNext(Function<Throwable, Observable<T>> resumeFunction) {
    return new Observable<>() {
      @Override
      public void subscribeActual(Observer<T> observer) {
        Observable.this.subscribe(
            item -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onNext(item);
            },
            throwable -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              try {
                resumeFunction
                    .apply(throwable)
                    .subscribe(
                        x -> {
                          if (observer instanceof Disposable
                              && ((Disposable) observer).isDisposed()) return;
                          observer.onNext(x);
                        },
                        t -> {
                          if (observer instanceof Disposable
                              && ((Disposable) observer).isDisposed()) return;
                          observer.onError(t);
                        },
                        () -> {
                          if (observer instanceof Disposable
                              && ((Disposable) observer).isDisposed()) return;
                          observer.onComplete();
                        });
              } catch (Exception e) {
                if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
                observer.onError(e);
              }
            },
            () -> {
              if (observer instanceof Disposable && ((Disposable) observer).isDisposed()) return;
              observer.onComplete();
            });
      }
    };
  }
}
