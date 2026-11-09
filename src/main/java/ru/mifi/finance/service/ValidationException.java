package ru.mifi.finance.service;

/** Некорректные данные от пользователя. */
public class ValidationException extends RuntimeException {
  public ValidationException(String message) {
    super(message);
  }
}
