package ru.mifi.finance.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mifi.finance.repository.FileUserRepository;
import ru.mifi.finance.repository.FileWalletRepository;
import ru.mifi.finance.service.AuthService;
import ru.mifi.finance.service.FinanceService;
import ru.mifi.finance.service.ReportService;
import ru.mifi.finance.service.Session;

/** Интеграционные сценарии: полный цикл через консоль с сохранением на диск. */
class ConsoleAppTest {

  @TempDir Path dir;

  private String run(String input) throws Exception {
    FinanceService finance = new FinanceService(Clock.systemDefaultZone());
    Session session =
        new Session(
            new AuthService(new FileUserRepository(dir.resolve("users.json"))),
            new FileWalletRepository(dir.resolve("wallets")),
            finance);
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    new ConsoleApp(
            session,
            finance,
            new ReportService(finance),
            new BufferedReader(new StringReader(input)),
            new PrintStream(buf, true, StandardCharsets.UTF_8))
        .run();
    return buf.toString(StandardCharsets.UTF_8);
  }

  @Test
  void exampleFromAssignment() throws Exception {
    String out =
        run(
            String.join(
                "\n",
                "register alice pass1",
                "login alice pass1",
                "expense 300 Еда",
                "expense 500 Еда",
                "expense 3000 Развлечения",
                "expense 3000 \"Коммунальные услуги\"",
                "expense 1500 Такси",
                "income 20000 Зарплата",
                "income 40000 Зарплата",
                "income 3000 Бонус",
                "budget Еда 4000",
                "budget Развлечения 3000",
                "budget \"Коммунальные услуги\" 2500",
                "summary",
                "exit",
                ""));
    assertTrue(out.contains("Общий доход: 63,000.0"), out);
    assertTrue(out.contains("Коммунальные услуги: 2500.0, Оставшийся бюджет: -500.0"), out);
    assertTrue(out.contains("[!] Превышен бюджет по категории «Коммунальные услуги»"), out);
  }

  @Test
  void walletSavedOnExitAndLoadedOnLogin() throws Exception {
    run("register bob pass1\nlogin bob pass1\nincome 500 Зарплата\nexit\n");
    String out = run("login bob pass1\nbalance\nexit\n");
    assertTrue(out.contains("Баланс: 500.0"), out);
  }

  @Test
  void transferBetweenUsers() throws Exception {
    run(
        "register alice pass1\nregister bob pass2\n"
            + "login alice pass1\nincome 1000 Зарплата\ntransfer bob 400 подарок\nexit\n");
    String out = run("login bob pass2\nhistory\nbalance\nexit\n");
    assertTrue(out.contains("Перевод от пользователя alice"), out);
    assertTrue(out.contains("Баланс: 400.0"), out);
  }

  @Test
  void validationMessages() throws Exception {
    String out =
        run(
            "income 100 Еда\nlogin x y\nregister carl pass1\nlogin carl pass1\n"
                + "income abc Еда\nexpense -5 Еда\nstats Путешествия\nfoo\nexit\n");
    assertTrue(out.contains("Сначала войдите"), out);
    assertTrue(out.contains("Неверный логин или пароль"), out);
    assertTrue(out.contains("Сумма должна быть числом"), out);
    assertTrue(out.contains("Сумма должна быть больше нуля"), out);
    assertTrue(out.contains("Категории не найдены: Путешествия"), out);
    assertTrue(out.contains("Неизвестная команда"), out);
  }

  @Test
  void reportAndExportFiles() throws Exception {
    Path report = dir.resolve("report.txt");
    Path csv = dir.resolve("ops.csv");
    run(
        "register dan pass1\nlogin dan pass1\nincome 100 Зарплата\n"
            + "report "
            + report
            + "\nexport csv "
            + csv
            + "\nexit\n");
    assertTrue(Files.readString(report).contains("Общий доход: 100.0"));
    assertEquals(2, Files.readAllLines(csv).size());
  }

  @Test
  void parserHandlesQuotes() {
    assertEquals(
        List.of("expense", "3000", "Коммунальные услуги", "за", "май"),
        CommandParser.split("expense 3000 \"Коммунальные услуги\" за май"));
  }
}
