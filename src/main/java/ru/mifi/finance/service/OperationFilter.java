package ru.mifi.finance.service;

import java.time.LocalDate;
import java.util.Set;
import ru.mifi.finance.domain.Operation;

/**
 * Фильтр операций по категориям и периоду. Пустой набор категорий и null-даты означают «без
 * ограничений».
 */
public record OperationFilter(Set<String> categories, LocalDate from, LocalDate to) {

  public static final OperationFilter ALL = new OperationFilter(Set.of(), null, null);

  public OperationFilter {
    categories = categories == null ? Set.of() : Set.copyOf(categories);
    if (from != null && to != null && from.isAfter(to)) {
      throw new ValidationException("Начало периода позже его конца");
    }
  }

  public boolean matches(Operation op) {
    if (!categories.isEmpty() && !categories.contains(op.category())) {
      return false;
    }
    LocalDate date = op.time().toLocalDate();
    if (from != null && date.isBefore(from)) {
      return false;
    }
    return to == null || !date.isAfter(to);
  }
}
