package ru.mifi.finance.repository;

/** Ошибка чтения или записи данных на диск. */
public class StorageException extends RuntimeException {
  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
