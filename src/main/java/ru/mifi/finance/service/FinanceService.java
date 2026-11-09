package ru.mifi.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import ru.mifi.finance.domain.BudgetStatus;
import ru.mifi.finance.domain.Operation;
import ru.mifi.finance.domain.OperationType;
import ru.mifi.finance.domain.Wallet;

/** Операции с кошельком: доходы, расходы, бюджеты, подсчёты и оповещения. */
public final class FinanceService {

  public static final String TRANSFER_CATEGORY = "Перевод";
  private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
  private static final BigDecimal WARNING_SHARE = new BigDecimal("0.8");

  private final Clock clock;

  public FinanceService(Clock clock) {
    this.clock = clock;
  }

  /** Добавляет доход и возвращает оповещения. */
  public List<String> addIncome(Wallet wallet, String category, BigDecimal amount, String comment) {
    wallet.add(
        new Operation(
            OperationType.INCOME,
            cleanCategory(category),
            checkAmount(amount),
            LocalDateTime.now(clock),
            comment));
    return generalAlerts(wallet);
  }

  /** Добавляет расход и возвращает оповещения (бюджет, перерасход, баланс). */
  public List<String> addExpense(
      Wallet wallet, String category, BigDecimal amount, String comment) {
    String cat = cleanCategory(category);
    wallet.add(
        new Operation(
            OperationType.EXPENSE, cat, checkAmount(amount), LocalDateTime.now(clock), comment));
    List<String> alerts = new ArrayList<>(budgetAlerts(wallet, cat));
    alerts.addAll(generalAlerts(wallet));
    return alerts;
  }

  public void setBudget(Wallet wallet, String category, BigDecimal limit) {
    wallet.setBudget(cleanCategory(category), checkAmount(limit));
  }

  public void removeBudget(Wallet wallet, String category) {
    if (!wallet.removeBudget(cleanCategory(category))) {
      throw new ValidationException("Бюджет для категории «" + category + "» не задан");
    }
  }

  public void renameCategory(Wallet wallet, String from, String to) {
    String src = cleanCategory(from);
    String dst = cleanCategory(to);
    if (!categories(wallet).contains(src)) {
      throw new ValidationException("Категория «" + src + "» не найдена");
    }
    if (src.equals(dst)) {
      throw new ValidationException("Новое название совпадает со старым");
    }
    wallet.renameCategory(src, dst);
  }

  public BigDecimal total(Wallet wallet, OperationType type, OperationFilter filter) {
    return wallet.operations().stream()
        .filter(op -> op.type() == type && filter.matches(op))
        .map(Operation::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Суммы по категориям (отсортированы по названию). */
  public Map<String, BigDecimal> byCategory(
      Wallet wallet, OperationType type, OperationFilter filter) {
    Map<String, BigDecimal> result = new TreeMap<>();
    for (Operation op : wallet.operations()) {
      if (op.type() == type && filter.matches(op)) {
        result.merge(op.category(), op.amount(), BigDecimal::add);
      }
    }
    return result;
  }

  /** Категории из фильтра, которых нет ни в операциях, ни в бюджетах кошелька. */
  public Set<String> unknownCategories(Wallet wallet, OperationFilter filter) {
    Set<String> known = categories(wallet);
    Set<String> missing = new LinkedHashSet<>();
    for (String c : filter.categories()) {
      if (!known.contains(c)) {
        missing.add(c);
      }
    }
    return missing;
  }

  public Set<String> categories(Wallet wallet) {
    Set<String> result = new LinkedHashSet<>(wallet.budgets().keySet());
    wallet.operations().forEach(op -> result.add(op.category()));
    return result;
  }

  public List<BudgetStatus> budgetStatuses(Wallet wallet) {
    List<BudgetStatus> result = new ArrayList<>();
    Map<String, BigDecimal> spent = byCategory(wallet, OperationType.EXPENSE, OperationFilter.ALL);
    wallet
        .budgets()
        .forEach(
            (cat, limit) ->
                result.add(new BudgetStatus(cat, limit, spent.getOrDefault(cat, BigDecimal.ZERO))));
    return result;
  }

  /** Перевод: расход у отправителя и доход у получателя в категории «Перевод». */
  public List<String> transfer(Wallet from, Wallet to, BigDecimal amount, String comment) {
    if (from.owner().equals(to.owner())) {
      throw new ValidationException("Нельзя перевести деньги самому себе");
    }
    BigDecimal value = checkAmount(amount);
    if (from.balance().compareTo(value) < 0) {
      throw new ValidationException(
          "Недостаточно средств: баланс " + from.balance().toPlainString());
    }
    LocalDateTime now = LocalDateTime.now(clock);
    String note = comment == null || comment.isBlank() ? "" : " (" + comment + ")";
    from.add(
        new Operation(
            OperationType.EXPENSE,
            TRANSFER_CATEGORY,
            value,
            now,
            "Перевод пользователю " + to.owner() + note));
    to.add(
        new Operation(
            OperationType.INCOME,
            TRANSFER_CATEGORY,
            value,
            now,
            "Перевод от пользователя " + from.owner() + note));
    List<String> alerts = new ArrayList<>(budgetAlerts(from, TRANSFER_CATEGORY));
    alerts.addAll(generalAlerts(from));
    return alerts;
  }

  /** Все актуальные оповещения по кошельку. */
  public List<String> allAlerts(Wallet wallet) {
    List<String> alerts = new ArrayList<>();
    for (String cat : wallet.budgets().keySet()) {
      alerts.addAll(budgetAlerts(wallet, cat));
    }
    alerts.addAll(generalAlerts(wallet));
    return alerts;
  }

  private List<String> budgetAlerts(Wallet wallet, String category) {
    BigDecimal limit = wallet.budgets().get(category);
    if (limit == null) {
      return List.of();
    }
    BigDecimal spent =
        total(wallet, OperationType.EXPENSE, new OperationFilter(Set.of(category), null, null));
    if (spent.compareTo(limit) > 0) {
      return List.of(
          "Превышен бюджет по категории «"
              + category
              + "»: лимит "
              + limit.toPlainString()
              + ", потрачено "
              + spent.toPlainString()
              + " (перерасход "
              + spent.subtract(limit).toPlainString()
              + ")");
    }
    if (spent.compareTo(limit) == 0) {
      return List.of("Бюджет по категории «" + category + "» израсходован полностью");
    }
    if (spent.compareTo(limit.multiply(WARNING_SHARE)) >= 0) {
      int percent =
          spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP).intValue();
      return List.of(
          "Израсходовано "
              + percent
              + "% бюджета по категории «"
              + category
              + "», осталось "
              + limit.subtract(spent).toPlainString());
    }
    return List.of();
  }

  private List<String> generalAlerts(Wallet wallet) {
    List<String> alerts = new ArrayList<>();
    BigDecimal income = total(wallet, OperationType.INCOME, OperationFilter.ALL);
    BigDecimal expense = total(wallet, OperationType.EXPENSE, OperationFilter.ALL);
    if (expense.compareTo(income) > 0) {
      alerts.add(
          "Расходы ("
              + expense.toPlainString()
              + ") превысили доходы ("
              + income.toPlainString()
              + ")");
    } else if (expense.signum() > 0 && expense.compareTo(income) == 0) {
      alerts.add("Баланс равен нулю: все доходы потрачены");
    }
    return alerts;
  }

  static String cleanCategory(String category) {
    if (category == null || category.isBlank()) {
      throw new ValidationException("Категория не может быть пустой");
    }
    String c = category.trim();
    if (c.length() > 50) {
      throw new ValidationException("Название категории длиннее 50 символов");
    }
    return c;
  }

  static BigDecimal checkAmount(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new ValidationException("Сумма должна быть больше нуля");
    }
    if (amount.compareTo(MAX_AMOUNT) >= 0) {
      throw new ValidationException("Слишком большая сумма");
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new ValidationException("У суммы не может быть больше двух знаков после запятой");
    }
    return amount;
  }

  /** Разбирает сумму, допуская запятую как десятичный разделитель. */
  public static BigDecimal parseAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ValidationException("Не указана сумма");
    }
    try {
      return checkAmount(new BigDecimal(raw.trim().replace(',', '.')));
    } catch (NumberFormatException e) {
      throw new ValidationException("Сумма должна быть числом: " + raw);
    }
  }

  /** Для вывода: бюджеты по категориям с остатком. */
  public Map<String, BudgetStatus> budgetMap(Wallet wallet) {
    Map<String, BudgetStatus> map = new LinkedHashMap<>();
    budgetStatuses(wallet).forEach(s -> map.put(s.category(), s));
    return map;
  }
}
