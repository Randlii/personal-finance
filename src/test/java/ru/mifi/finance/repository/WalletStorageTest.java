package ru.mifi.finance.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mifi.finance.domain.Wallet;
import ru.mifi.finance.service.FinanceService;
import ru.mifi.finance.service.ReportService;

class WalletStorageTest {

  @TempDir Path dir;
  private final FinanceService finance = new FinanceService(Clock.systemUTC());

  private Wallet sample() {
    Wallet w = new Wallet("alice");
    finance.addIncome(w, "Зарплата", new BigDecimal("1000.50"), "аванс, ноябрь");
    finance.addExpense(w, "Коммунальные услуги", new BigDecimal("300"), "");
    finance.setBudget(w, "Коммунальные услуги", new BigDecimal("500"));
    return w;
  }

  @Test
  void saveAndLoadRoundTrip() {
    FileWalletRepository repo = new FileWalletRepository(dir);
    repo.save(sample());
    Wallet loaded = repo.load("alice").orElseThrow();
    assertEquals(2, loaded.operations().size());
    assertEquals(0, new BigDecimal("700.50").compareTo(loaded.balance()));
    assertEquals(0, new BigDecimal("500").compareTo(loaded.budgets().get("Коммунальные услуги")));
    assertTrue(repo.load("nobody").isEmpty());
  }

  @Test
  void exportImportJson() {
    ReportService reports = new ReportService(finance);
    Path file = dir.resolve("export.json");
    reports.exportJson(sample(), file);
    Wallet other = new Wallet("bob");
    assertEquals(2, reports.importJson(other, file));
    assertEquals("bob", other.owner());
    assertEquals(0, new BigDecimal("700.50").compareTo(other.balance()));
  }

  @Test
  void exportCsvEscapesCommas() throws Exception {
    Path file = dir.resolve("ops.csv");
    new ReportService(finance).exportCsv(sample(), file);
    String csv = Files.readString(file);
    assertTrue(csv.startsWith("type,category,amount,time,comment"));
    assertTrue(csv.contains("\"аванс, ноябрь\""));
  }
}
