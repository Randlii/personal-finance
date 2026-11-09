package ru.mifi.finance.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Кошелёк пользователя: все операции и бюджеты по категориям расходов. */
public final class Wallet {

  private final String owner;
  private final List<Operation> operations = new ArrayList<>();
  private final Map<String, BigDecimal> budgets = new LinkedHashMap<>();

  public Wallet(String owner) {
    this.owner = Objects.requireNonNull(owner);
  }

  public String owner() {
    return owner;
  }

  public void add(Operation operation) {
    operations.add(Objects.requireNonNull(operation));
  }

  public List<Operation> operations() {
    return Collections.unmodifiableList(operations);
  }

  public Map<String, BigDecimal> budgets() {
    return Collections.unmodifiableMap(budgets);
  }

  public void setBudget(String category, BigDecimal limit) {
    budgets.put(category, limit);
  }

  public boolean removeBudget(String category) {
    return budgets.remove(category) != null;
  }

  /** Переименовывает категорию во всех операциях и бюджетах. */
  public void renameCategory(String from, String to) {
    operations.replaceAll(op -> op.category().equals(from) ? op.withCategory(to) : op);
    if (budgets.containsKey(from)) {
      BigDecimal existing = budgets.remove(from);
      budgets.merge(to, existing, BigDecimal::add);
    }
  }

  public BigDecimal balance() {
    BigDecimal sum = BigDecimal.ZERO;
    for (Operation op : operations) {
      sum = op.type() == OperationType.INCOME ? sum.add(op.amount()) : sum.subtract(op.amount());
    }
    return sum;
  }
}
