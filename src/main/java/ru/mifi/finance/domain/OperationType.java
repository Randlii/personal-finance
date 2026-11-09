package ru.mifi.finance.domain;

/** Тип финансовой операции. */
public enum OperationType {
  INCOME("Доход"),
  EXPENSE("Расход");

  private final String title;

  OperationType(String title) {
    this.title = title;
  }

  public String title() {
    return title;
  }
}
