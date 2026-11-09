package ru.mifi.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mifi.finance.domain.BudgetStatus;
import ru.mifi.finance.domain.OperationType;
import ru.mifi.finance.domain.Wallet;

class FinanceServiceTest {

  private final FinanceService finance =
      new FinanceService(Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC));
  private Wallet wallet;

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  @BeforeEach
  void setUp() {
    wallet = new Wallet("alice");
  }

  /** Данные из примера в задании. */
  private void fillExample() {
    finance.addExpense(wallet, "Еда", bd("300"), "");
    finance.addExpense(wallet, "Еда", bd("500"), "");
    finance.addExpense(wallet, "Развлечения", bd("3000"), "");
    finance.addExpense(wallet, "Коммунальные услуги", bd("3000"), "");
    finance.addExpense(wallet, "Такси", bd("1500"), "");
    finance.addIncome(wallet, "Зарплата", bd("20000"), "");
    finance.addIncome(wallet, "Зарплата", bd("40000"), "");
    finance.addIncome(wallet, "Бонус", bd("3000"), "");
    finance.setBudget(wallet, "Еда", bd("4000"));
    finance.setBudget(wallet, "Развлечения", bd("3000"));
    finance.setBudget(wallet, "Коммунальные услуги", bd("2500"));
  }

  @Test
  void totalsMatchExample() {
    fillExample();
    assertEquals(
        0, bd("63000").compareTo(finance.total(wallet, OperationType.INCOME, OperationFilter.ALL)));
    assertEquals(
        0, bd("8300").compareTo(finance.total(wallet, OperationType.EXPENSE, OperationFilter.ALL)));
    assertEquals(
        0,
        bd("60000")
            .compareTo(
                finance
                    .byCategory(wallet, OperationType.INCOME, OperationFilter.ALL)
                    .get("Зарплата")));
  }

  @Test
  void budgetRemainingMatchesExample() {
    fillExample();
    var map = finance.budgetMap(wallet);
    assertEquals(0, bd("-500").compareTo(map.get("Коммунальные услуги").remaining()));
    assertEquals(0, bd("3200").compareTo(map.get("Еда").remaining()));
    assertEquals(0, bd("0").compareTo(map.get("Развлечения").remaining()));
    assertTrue(map.get("Коммунальные услуги").isExceeded());
  }

  @Test
  void summaryFormatMatchesExample() {
    fillExample();
    String s = new ReportService(finance).summary(wallet, OperationFilter.ALL);
    assertTrue(s.contains("Общий доход: 63,000.0"), s);
    assertTrue(s.contains("Зарплата: 60,000.0"), s);
    assertTrue(s.contains("Общие расходы: 8,300.0"), s);
    assertTrue(s.contains("Коммунальные услуги: 2500.0, Оставшийся бюджет: -500.0"), s);
    assertTrue(s.contains("Еда: 4000.0, Оставшийся бюджет: 3200.0"), s);
    assertTrue(s.contains("Развлечения: 3000.0, Оставшийся бюджет: 0.0"), s);
  }

  @Test
  void alertWhenBudgetExceeded() {
    finance.addIncome(wallet, "Зарплата", bd("10000"), "");
    finance.setBudget(wallet, "Еда", bd("1000"));
    List<String> alerts = finance.addExpense(wallet, "Еда", bd("1200"), "");
    assertTrue(alerts.stream().anyMatch(a -> a.startsWith("Превышен бюджет")), alerts.toString());
  }

  @Test
  void warningAt80Percent() {
    finance.addIncome(wallet, "Зарплата", bd("10000"), "");
    finance.setBudget(wallet, "Еда", bd("1000"));
    assertTrue(finance.addExpense(wallet, "Еда", bd("500"), "").isEmpty());
    List<String> alerts = finance.addExpense(wallet, "Еда", bd("300"), "");
    assertTrue(alerts.get(0).contains("80%"), alerts.toString());
  }

  @Test
  void alertWhenExpensesExceedIncome() {
    finance.addIncome(wallet, "Зарплата", bd("100"), "");
    List<String> alerts = finance.addExpense(wallet, "Такси", bd("150"), "");
    assertTrue(alerts.stream().anyMatch(a -> a.contains("превысили доходы")));
  }

  @Test
  void alertOnZeroBalance() {
    finance.addIncome(wallet, "Зарплата", bd("100"), "");
    List<String> alerts = finance.addExpense(wallet, "Такси", bd("100"), "");
    assertTrue(alerts.stream().anyMatch(a -> a.contains("Баланс равен нулю")));
  }

  @Test
  void statsBySeveralCategories() {
    fillExample();
    OperationFilter f = new OperationFilter(Set.of("Еда", "Такси"), null, null);
    assertEquals(0, bd("2300").compareTo(finance.total(wallet, OperationType.EXPENSE, f)));
  }

  @Test
  void unknownCategoriesReported() {
    fillExample();
    OperationFilter f = new OperationFilter(Set.of("Еда", "Путешествия"), null, null);
    assertEquals(Set.of("Путешествия"), finance.unknownCategories(wallet, f));
  }

  @Test
  void periodFilter() {
    fillExample();
    OperationFilter past =
        new OperationFilter(Set.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31));
    assertEquals(0, BigDecimal.ZERO.compareTo(finance.total(wallet, OperationType.INCOME, past)));
    assertThrows(
        ValidationException.class,
        () -> new OperationFilter(Set.of(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
  }

  @Test
  void renameCategoryMovesOperationsAndBudget() {
    fillExample();
    finance.renameCategory(wallet, "Еда", "Продукты");
    BudgetStatus s = finance.budgetMap(wallet).get("Продукты");
    assertEquals(0, bd("800").compareTo(s.spent()));
    assertThrows(ValidationException.class, () -> finance.renameCategory(wallet, "Нет", "X"));
  }

  @Test
  void editAndRemoveBudget() {
    finance.setBudget(wallet, "Еда", bd("100"));
    finance.setBudget(wallet, "Еда", bd("200"));
    assertEquals(0, bd("200").compareTo(wallet.budgets().get("Еда")));
    finance.removeBudget(wallet, "Еда");
    assertThrows(ValidationException.class, () -> finance.removeBudget(wallet, "Еда"));
  }

  @Test
  void invalidAmountsRejected() {
    assertThrows(ValidationException.class, () -> FinanceService.parseAmount("abc"));
    assertThrows(ValidationException.class, () -> FinanceService.parseAmount("-5"));
    assertThrows(ValidationException.class, () -> FinanceService.parseAmount("0"));
    assertThrows(ValidationException.class, () -> FinanceService.parseAmount("1.234"));
    assertEquals(0, bd("10.5").compareTo(FinanceService.parseAmount("10,5")));
    assertThrows(ValidationException.class, () -> finance.addIncome(wallet, " ", bd("1"), ""));
  }

  @Test
  void transferCreatesExpenseAndIncome() {
    Wallet bob = new Wallet("bob");
    finance.addIncome(wallet, "Зарплата", bd("1000"), "");
    finance.transfer(wallet, bob, bd("300"), "долг");
    assertEquals(0, bd("700").compareTo(wallet.balance()));
    assertEquals(0, bd("300").compareTo(bob.balance()));
    assertEquals(FinanceService.TRANSFER_CATEGORY, bob.operations().get(0).category());
  }

  @Test
  void transferNeedsEnoughMoney() {
    Wallet bob = new Wallet("bob");
    assertThrows(ValidationException.class, () -> finance.transfer(wallet, bob, bd("1"), ""));
    assertThrows(
        ValidationException.class,
        () -> finance.transfer(wallet, new Wallet("alice"), bd("1"), ""));
  }
}
