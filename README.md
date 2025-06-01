# 📄 Отчет по реализации RxJava-подобной библиотеки

## 1. Архитектура системы

Реализованная система представляет собой упрощенную версию реактивной библиотеки в духе RxJava. Она поддерживает асинхронную обработку данных, преобразование элементов, обработку ошибок и управление потоками выполнения.

### Основные компоненты:

| Компонент | Назначение |
|----------|------------|
| **`Observable<T>`** | Источник данных, позволяет подписываться на события (`onNext`, `onError`, `onComplete`) |
| **`Observer<T>`** | Потребитель данных: получает элементы (`onNext`), ошибки (`onError`) и завершение (`onComplete`) |
| **`Disposable`** | Интерфейс для отмены подписки: `dispose()` и `isDisposed()` |
| **`Scheduler`** | Абстракция для управления потоками выполнения |
| **`ObservableOnSubscribe<T>`** | Функциональный интерфейс, используемый для создания Observable через `create(...)` |

### Принципы работы:

- **Ленивая инициализация**: данные эмитятся только при вызове метода `subscribe()`
- **Иммутабельность**: каждый оператор создаёт новый `Observable`
- **Цепочка вызовов**: операторы можно комбинировать в цепочки
- **Распространение ошибок**: ошибки передаются до первого обработчика (`onError`)
- **Подписка с отменой**: поддержка `Disposable` для контроля жизненного цикла подписки

---

## 2. Принципы работы Schedulers

Планировщики (`Schedulers`) позволяют управлять тем, в каком потоке будет происходить выполнение кода — подписка или обработка событий.

### Типы планировщиков:

| Планировщик | Реализация | Назначение |
|-------------|------------|------------|
| `Schedulers.io()` | `IOThreadScheduler` | Для I/O-операций (сеть, файлы) |
| `Schedulers.computation()` | `ComputationScheduler` | Для CPU-интенсивных задач |
| `Schedulers.single()` | `SingleThreadScheduler` | Для последовательного выполнения |

### Подробности реализации:

- **`IOThreadScheduler`** — использует `CachedThreadPool`, создающий новые потоки по мере необходимости.
- **`ComputationScheduler`** — использует `FixedThreadPool`, количество потоков = число ядер процессора.
- **`SingleThreadScheduler`** — один выделенный поток, гарантирует порядок выполнения задач.

### Управление жизненным циклом:

Все планировщики реализуют интерфейс `AutoCloseable`, что позволяет автоматически освобождать ресурсы потоков при использовании try-with-resources или вручную через вызов метода close(). Это гарантирует корректное завершение работы потоков и предотвращает утечки ресурсов.

### Методы управления потоками:

- **`subscribeOn(Scheduler scheduler)`** — определяет, в каком потоке будет выполняться подписка.
- **`observeOn(Scheduler scheduler)`** — определяет, в каком потоке будут обрабатываться события (`onNext`, `onError`, `onComplete`).

---

## 3. Обработка ошибок

Обработка ошибок реализована через механизм передачи исключений в метод `onError(Throwable t)`.

### Поддерживаемые операторы:

| Оператор | Описание |
|---------|----------|
| `onErrorReturn(T value)` | Возвращает значение по умолчанию при ошибке и завершает поток |
| `onErrorReturn(Function<Throwable, T> valueSupplier)` | Возвращает динамическое значение при ошибке |
| `onErrorResumeNext(Function<Throwable, Observable<T>> resumeFunction)` | Возобновляет поток с нового Observable |

### Пример:

```java
Observable.create(emitter -> {
    emitter.onNext(1);
    emitter.onError(new RuntimeException("Test"));
})
.onErrorReturn(-1)
.subscribe(System.out::println);
```

**Вывод:**
```
1
-1
```

---

### 4. Реализация `Disposable`

Интерфейс `Disposable` позволяет отменять подписку на `Observable`.

#### Как работает:
- При вызове `dispose()` устанавливается флаг `disposed = true`.
- Все последующие вызовы `onNext`, `onError`, `onComplete` игнорируются.

#### Класс `DisposableObserver<T>`:
Этот класс оборачивает наблюдателя и добавляет функционал отмены подписки.

```java
public abstract class DisposableObserver<T> implements Observer<T>, Disposable { ... }
```

#### Интеграция с `Observable`:
Все подписчики автоматически оборачиваются в `DisposableObserver`, что даёт возможность отменять подписку.

Пример использования:
```java
Disposable disposable = Observable.just(1, 2, 3).subscribe(System.out::println);
disposable.dispose(); // Прерывает получение новых элементов
```

---

## 5. Процесс тестирования

Тестирование проводилось с использованием **JUnit 5**. Были проверены следующие аспекты:

### Категории тестов:

| Тестовая группа | Что проверяется |
|------------------|------------------|
| Базовые тесты Observable | Создание, подписка, получение элементов, обработка ошибок |
| Операторы | map, filter, flatMap, range, take, distinct, debounce |
| Schedulers | subscribeOn, observeOn, многопоточная работа |
| Disposable | Отмена подписки, состояние isDisposed |
| Обработка ошибок | onErrorReturn, onErrorResumeNext |

### Примеры юнит-тестов:

#### Тест `map`:

```java
@Test
void testMapOperator() {
    AtomicInteger result = new AtomicInteger();
    Observable.just(5)
        .map(x -> x * x)
        .subscribe(result::set);
    assertEquals(25, result.get());
}
```

#### Тест `flatMap`:

```java
@Test
void testFlatMapOperator() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger count = new AtomicInteger();

    Observable.just(2, 3)
        .flatMap(x -> Observable.range(1, x))
        .subscribe(
            item -> {
                count.incrementAndGet();
                latch.countDown();
            },
            Throwable::printStackTrace
        );

    latch.await();
    assertEquals(5, count.get());
}
```

#### Тест `Disposable`:

```java
@Test
void testDisposableCancelSubscription() throws InterruptedException {
    AtomicInteger count = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1);

    Disposable disposable = Observable.range(1, 100)
        .subscribe(
            item -> {
                if (item > 10) disposable.dispose();
                else count.incrementAndGet();
            },
            error -> fail(),
            latch::countDown
        );

    latch.await();
    assertEquals(10, count.get());
}
```

---

## 6. Примеры использования

### Базовый пример:

```java
Observable.just("Hello", "World")
    .map(String::toUpperCase)
    .subscribe(
        System.out::println,
        Throwable::printStackTrace
    );
// Выведет:
// HELLO
// WORLD
```

### Работа с ошибками:

```java
Observable.create(emitter -> {
    emitter.onNext(1);
    emitter.onError(new RuntimeException("Test"));
})
.onErrorReturn(-1)
.subscribe(System.out::println);
// Выведет:
// 1
// -1
```

### Многопоточная обработка:

```java
try (Scheduler computation = Schedulers.computation(); Scheduler single = Schedulers.single()) {
    Observable.range(1, 10)
        .subscribeOn(computation)
        .filter(x -> x % 2 == 0)
        .observeOn(single)
        .subscribe(
            x -> System.out.println(Thread.currentThread().getName() + ": " + x));
    Thread.sleep(200);
} catch (Exception e) {
}
```

### Отмена подписки:

```java
try (Scheduler single = Schedulers.single()) {
    Disposable disposable = Observable.range(1,1000)
        .observeOn(single)
        .subscribe(System.out::println);
    disposable.dispose();
} catch (Exception e) {
}
// Подписка прервана, новые элементы не выводятся
```

---

## 7. Заключение

Реализованная библиотека предоставляет:

- **Основные механизмы реактивного программирования** (паттерн Наблюдатель, ленивые вычисления).
- **Набор операторов** для преобразования, фильтрации и обработки данных.
- **Гибкую систему управления потоками** с поддержкой нескольких типов планировщиков.
- **Надежную обработку ошибок** с возможностью восстановления или продолжения потока.
- **Механизм отмены подписки**, обеспечивающий контроль над жизненным циклом потока.

Система демонстрирует ключевые принципы реактивного программирования и может быть расширена для поддержки таких функций, как backpressure, Flow API, retry, timeout и другие.

---

## ✅ Что можно добавить в будущем:

- **CompositeDisposable** — для управления несколькими подписками.
- **Flow API / Publisher** — для совместимости с Reactive Streams.
- **Backpressure** — для контроля скорости эмиссии данных.
- **Таймеры и повторы** (`retry`, `timeout`, `interval`).
- **Диаграммы UML** — для наглядности архитектуры.

---