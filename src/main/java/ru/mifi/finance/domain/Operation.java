package ru.mifi.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Одна операция в кошельке: доход или расход по категории. */
public record Operation(
    OperationType type, String category, BigDecimal amount, LocalDateTime time, String comment) {

  public Operation {
    Objects.requireNonNull(type);
    Objects.requireNonNull(category);
    Objects.requireNonNull(amount);
    Objects.requireNonNull(time);
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("Сумма операции должна быть положительной");
    }
    comment = comment == null ? "" : comment;
  }

  public Operation withCategory(String newCategory) {
    return new Operation(type, newCategory, amount, time, comment);
  }
}
