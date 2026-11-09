package ru.mifi.finance.domain;

import java.math.BigDecimal;

/** Состояние бюджета по категории: лимит, потрачено и остаток. */
public record BudgetStatus(String category, BigDecimal limit, BigDecimal spent) {

  public BigDecimal remaining() {
    return limit.subtract(spent);
  }

  public boolean isExceeded() {
    return spent.compareTo(limit) > 0;
  }
}
