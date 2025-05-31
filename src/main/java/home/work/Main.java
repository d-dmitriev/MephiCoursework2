package home.work;

import home.work.observers.Disposable;
import home.work.observers.Observable;
import home.work.observers.Scheduler;
import home.work.schedulers.Schedulers;
import java.io.IOException;

public class Main {
  public static void main(String[] args) throws Exception {
    System.out.println("=== Пример с Observable.range с операторами и schedulers ===");
    try (Scheduler io = Schedulers.io();
        Scheduler single = Schedulers.single()) {
      Observable.range(1, 10)
          .filter(x -> x % 2 == 0)
          .map(x -> x * x)
          .flatMap(x -> Observable.just(x + "!"))
          .subscribeOn(io)
          .observeOn(single)
          .subscribe(
              item -> System.out.println("Получено: " + item),
              Throwable::printStackTrace,
              () -> System.out.println("Завершено"));
      Thread.sleep(200); // Чтобы успели выполниться асинхронные задачи
    }

    System.out.println("\n=== Пример с Observable.create и c onErrorReturn ===");
    Observable.create(
            emitter -> {
              long time = System.currentTimeMillis();
              if (time % 2 != 0) {
                emitter.onError(new IllegalStateException("Нечетная миллисекунда"));
              } else {
                emitter.onNext(1);
              }
            })
        .onErrorReturn("Ошибка: нечетная миллисекунда")
        .subscribe(
            item -> System.out.println("Получено: " + item),
            Throwable::printStackTrace,
            () -> System.out.println("Завершено"));

    System.out.println("\n=== Пример с Observable.create и onErrorResumeNext ===");
    Observable.create(
            emitter -> {
              emitter.onNext(1);
              emitter.onNext(2);
              emitter.onError(new RuntimeException("Ошибка!"));
            })
        .onErrorResumeNext(_ -> Observable.just(100, 200, 300))
        .subscribe(
            System.out::println,
            error -> System.err.println("Обработчик ошибок не должен быть вызван: " + error));

    System.out.println("\n=== Пример с Observable.onErrorReturn ===");
    Observable.create(
            emitter -> {
              emitter.onNext(1);
              emitter.onNext(2);
              emitter.onError(new RuntimeException("Something went wrong!"));
            })
        .onErrorReturn(-1) // При ошибке вернет -1 и завершится
        .subscribe(
            item -> System.out.println("Получено: " + item),
            error -> System.err.println("Обработчик ошибок не должен быть вызван: " + error),
            () -> System.out.println("Завершено"));

    System.out.println("\n=== Пример с Observable.onErrorReturn ===");
    Observable.create(
            emitter -> {
              emitter.onNext("Первый элемент");
              emitter.onError(new IOException("Файл не найден"));
            })
        .onErrorReturn(error -> "Запасной вариант для: " + error.getMessage())
        .subscribe(
            item -> System.out.println("Получено: " + item),
            _ -> System.err.println("Error handler should not be called"),
            () -> System.out.println("Завершено"));

    System.out.println("\n=== Пример с Observable.debounce ===");
    Observable.create(
            emitter -> {
              emitter.onNext("A");
              Thread.sleep(100);
              emitter.onNext("B");
              Thread.sleep(50);
              emitter.onNext("C");
              Thread.sleep(200);
              emitter.onNext("D");
              emitter.onComplete();
            })
        .debounce(150) // Пропускаем элементы с интервалом менее 150 мс
        .subscribe(System.out::println);

    System.out.println("\n=== Пример с Observable.range, distinct и take ===");
    Observable.range(1, 100, i -> (i + 1) % 10) // Генерируем числа от 1 до 100 с повторениями
        .distinct() // Оставляем только уникальные
        .take(5) // Берем первые 5
        .subscribe(System.out::println);

    System.out.println("\n=== Пример с Disposable (прерывание подписки) ===");
    try (Scheduler single = Schedulers.single()) {
      Disposable disposable =
          Observable.range(1, 100)
              .observeOn(single)
              .subscribe(
                  item -> {
                    System.out.println("Получено: " + item);
                    try {
                      // Имитация длительной обработки
                      Thread.sleep(50);
                    } catch (InterruptedException e) {
                      System.out.println("Подписка прервана");
                    }
                  },
                  Throwable::printStackTrace,
                  () -> System.out.println("Завершено"));
      System.out.println("=== Прерывание подписки ===");
      disposable.dispose();
    }
  }
}
