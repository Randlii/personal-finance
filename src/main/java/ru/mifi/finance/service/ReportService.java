package ru.mifi.finance.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import ru.mifi.finance.domain.BudgetStatus;
import ru.mifi.finance.domain.Operation;
import ru.mifi.finance.domain.OperationType;
import ru.mifi.finance.domain.Wallet;
import ru.mifi.finance.repository.WalletJson;

/** Текстовые отчёты, экспорт в CSV/JSON и импорт из JSON. */
public final class ReportService {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final FinanceService finance;

  public ReportService(FinanceService finance) {
    this.finance = finance;
  }

  /** Сводка в формате из задания. */
  public String summary(Wallet wallet, OperationFilter filter) {
    StringBuilder sb = new StringBuilder();
    sb.append("Общий доход: ")
        .append(grouped(finance.total(wallet, OperationType.INCOME, filter)))
        .append('\n');
    appendCategories(
        sb, "Доходы по категориям:", finance.byCategory(wallet, OperationType.INCOME, filter));
    sb.append("Общие расходы: ")
        .append(grouped(finance.total(wallet, OperationType.EXPENSE, filter)))
        .append('\n');
    appendCategories(
        sb, "Расходы по категориям:", finance.byCategory(wallet, OperationType.EXPENSE, filter));
    boolean unfiltered =
        filter.categories().isEmpty() && filter.from() == null && filter.to() == null;
    var budgets =
        finance.budgetStatuses(wallet).stream()
            .filter(
                s -> filter.categories().isEmpty() || filter.categories().contains(s.category()))
            .toList();
    if (!budgets.isEmpty()) {
      sb.append("Бюджет по категориям:\n");
      for (BudgetStatus s : budgets) {
        sb.append(s.category())
            .append(": ")
            .append(plain(s.limit()))
            .append(", Оставшийся бюджет: ")
            .append(plain(s.remaining()))
            .append('\n');
      }
    }
    if (unfiltered) {
      sb.append("Баланс: ").append(grouped(wallet.balance())).append('\n');
    }
    return sb.toString();
  }

  public void writeSummary(Wallet wallet, Path file) {
    write(file, summary(wallet, OperationFilter.ALL));
  }

  public void exportCsv(Wallet wallet, Path file) {
    StringBuilder sb = new StringBuilder("type,category,amount,time,comment\n");
    for (Operation op : wallet.operations()) {
      sb.append(op.type().name())
          .append(',')
          .append(csv(op.category()))
          .append(',')
          .append(op.amount().toPlainString())
          .append(',')
          .append(op.time().format(TIME))
          .append(',')
          .append(csv(op.comment()))
          .append('\n');
    }
    write(file, sb.toString());
  }

  public void exportJson(Wallet wallet, Path file) {
    write(file, WalletJson.toJson(wallet));
  }

  /**
   * Импортирует операции и бюджеты из JSON-файла в кошелёк. Бюджеты из файла заменяют одноимённые.
   *
   * @return число импортированных операций
   */
  public int importJson(Wallet wallet, Path file) {
    String json;
    try {
      json = Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ValidationException("Не удалось прочитать файл " + file + ": " + e.getMessage());
    }
    Wallet imported;
    try {
      imported = WalletJson.fromJson(json, wallet.owner());
    } catch (IllegalArgumentException e) {
      throw new ValidationException(e.getMessage());
    }
    imported.operations().forEach(wallet::add);
    imported.budgets().forEach(wallet::setBudget);
    return imported.operations().size();
  }

  public String history(Wallet wallet, int limit) {
    StringBuilder sb = new StringBuilder();
    var ops = wallet.operations();
    int start = Math.max(0, ops.size() - limit);
    sb.append(
        String.format(
            "%-16s %-7s %-22s %14s  %s%n", "Дата", "Тип", "Категория", "Сумма", "Комментарий"));
    for (int i = start; i < ops.size(); i++) {
      Operation op = ops.get(i);
      sb.append(
          String.format(
              Locale.US,
              "%-16s %-7s %-22s %,14.2f  %s%n",
              op.time().format(TIME),
              op.type().title(),
              op.category(),
              op.amount(),
              op.comment()));
    }
    return sb.toString();
  }

  public static String grouped(BigDecimal value) {
    return String.format(Locale.US, "%,.1f", value);
  }

  public static String plain(BigDecimal value) {
    return String.format(Locale.US, "%.1f", value);
  }

  private static void appendCategories(StringBuilder sb, String title, Map<String, BigDecimal> m) {
    if (m.isEmpty()) {
      return;
    }
    sb.append(title).append('\n');
    m.forEach((k, v) -> sb.append(k).append(": ").append(grouped(v)).append('\n'));
  }

  private static String csv(String value) {
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }

  private static void write(Path file, String content) {
    try {
      if (file.toAbsolutePath().getParent() != null) {
        Files.createDirectories(file.toAbsolutePath().getParent());
      }
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ValidationException("Не удалось записать файл " + file + ": " + e.getMessage());
    }
  }
}
