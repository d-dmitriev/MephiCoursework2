package home.work.observers;

@FunctionalInterface
public interface ObservableOnSubscribe<T> {
  void subscribe(Observer<T> emitter) throws Exception;
}
