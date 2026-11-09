package ru.mifi.finance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import ru.mifi.finance.cli.ConsoleApp;
import ru.mifi.finance.repository.FileUserRepository;
import ru.mifi.finance.repository.FileWalletRepository;
import ru.mifi.finance.service.AuthService;
import ru.mifi.finance.service.FinanceService;
import ru.mifi.finance.service.ReportService;
import ru.mifi.finance.service.Session;

/** Точка входа. Необязательный аргумент — каталог для данных (по умолчанию ./data). */
public final class Main {

  private Main() {}

  public static void main(String[] args) throws Exception {
    Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
    FinanceService finance = new FinanceService(Clock.systemDefaultZone());
    Session session =
        new Session(
            new AuthService(new FileUserRepository(dataDir.resolve("users.json"))),
            new FileWalletRepository(dataDir.resolve("wallets")),
            finance);
    new ConsoleApp(
            session,
            finance,
            new ReportService(finance),
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
            new PrintStream(System.out, true, StandardCharsets.UTF_8))
        .run();
  }
}
