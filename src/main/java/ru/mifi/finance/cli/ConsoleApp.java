package ru.mifi.finance.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ru.mifi.finance.domain.OperationType;
import ru.mifi.finance.domain.Wallet;
import ru.mifi.finance.repository.StorageException;
import ru.mifi.finance.service.FinanceService;
import ru.mifi.finance.service.OperationFilter;
import ru.mifi.finance.service.ReportService;
import ru.mifi.finance.service.Session;
import ru.mifi.finance.service.ValidationException;

/** Консольный интерфейс: цикл чтения команд. */
public final class ConsoleApp {

  private final Session session;
  private final FinanceService finance;
  private final ReportService reports;
  private final BufferedReader in;
  private final PrintStream out;

  public ConsoleApp(
      Session session,
      FinanceService finance,
      ReportService reports,
      BufferedReader in,
      PrintStream out) {
    this.session = session;
    this.finance = finance;
    this.reports = reports;
    this.in = in;
    this.out = out;
  }

  public void run() throws IOException {
    out.println("Управление личными финансами. Введите help, чтобы увидеть команды.");
    try {
      while (true) {
        out.print(session.isLoggedIn() ? session.login() + "> " : "> ");
        out.flush();
        String line = in.readLine();
        if (line == null || !handle(line.trim())) {
          break;
        }
      }
    } finally {
      if (session.isLoggedIn()) {
        session.logout();
        out.println("Данные кошелька сохранены.");
      }
    }
    out.println("До свидания!");
  }

  /** Обрабатывает команду. Возвращает false для выхода. */
  boolean handle(String line) {
    if (line.isEmpty()) {
      return true;
    }
    try {
      List<String> args = CommandParser.split(line);
      String cmd = args.get(0).toLowerCase();
      switch (cmd) {
        case "help" -> help();
        case "exit", "quit" -> {
          return false;
        }
        case "register" -> {
          session.register(
              arg(args, 1, "register <логин> <пароль>"), arg(args, 2, "register <логин> <пароль>"));
          out.println("Пользователь зарегистрирован. Теперь войдите: login <логин> <пароль>");
        }
        case "login" -> {
          session.login(
              arg(args, 1, "login <логин> <пароль>"), arg(args, 2, "login <логин> <пароль>"));
          out.println(
              "Добро пожаловать, "
                  + session.login()
                  + "! Баланс: "
                  + ReportService.grouped(session.wallet().balance()));
          printAlerts(finance.allAlerts(session.wallet()));
        }
        case "logout" -> {
          session.logout();
          out.println("Вы вышли, данные кошелька сохранены.");
        }
        case "income" -> addOperation(args, OperationType.INCOME);
        case "expense" -> addOperation(args, OperationType.EXPENSE);
        case "budget" -> budget(args);
        case "rename" -> {
          finance.renameCategory(
              wallet(),
              arg(args, 1, "rename <категория> <новое название>"),
              arg(args, 2, "rename <категория> <новое название>"));
          out.println("Категория переименована.");
        }
        case "balance" -> out.println("Баланс: " + ReportService.grouped(wallet().balance()));
        case "summary" -> out.print(reports.summary(wallet(), OperationFilter.ALL));
        case "stats" -> stats(args);
        case "budgets" -> budgets();
        case "categories" -> out.println(String.join(", ", finance.categories(wallet())));
        case "history" ->
            out.print(reports.history(wallet(), args.size() > 1 ? parseCount(args.get(1)) : 20));
        case "report" -> {
          Path file = Path.of(arg(args, 1, "report <файл>"));
          reports.writeSummary(wallet(), file);
          out.println("Отчёт записан в " + file.toAbsolutePath());
        }
        case "export" -> export(args);
        case "import" -> {
          int n = reports.importJson(wallet(), Path.of(arg(args, 1, "import <файл.json>")));
          out.println("Импортировано операций: " + n);
        }
        case "transfer" -> {
          String usage = "transfer <логин> <сумма> [комментарий]";
          String to = arg(args, 1, usage);
          BigDecimal amount = FinanceService.parseAmount(arg(args, 2, usage));
          List<String> alerts = session.transfer(to, amount, rest(args, 3));
          out.println("Переведено " + amount.toPlainString() + " пользователю " + to);
          printAlerts(alerts);
        }
        default -> out.println("Неизвестная команда: " + cmd + ". Введите help.");
      }
    } catch (ValidationException | StorageException e) {
      out.println("Ошибка: " + e.getMessage());
    }
    return true;
  }

  private void addOperation(List<String> args, OperationType type) {
    String name = type == OperationType.INCOME ? "income" : "expense";
    String usage = name + " <сумма> <категория> [комментарий]";
    BigDecimal amount = FinanceService.parseAmount(arg(args, 1, usage));
    String category = arg(args, 2, usage);
    String comment = rest(args, 3);
    List<String> alerts =
        type == OperationType.INCOME
            ? finance.addIncome(wallet(), category, amount, comment)
            : finance.addExpense(wallet(), category, amount, comment);
    out.println(
        type.title()
            + " "
            + amount.toPlainString()
            + " добавлен в категорию «"
            + category.trim()
            + "». Баланс: "
            + ReportService.grouped(wallet().balance()));
    printAlerts(alerts);
  }

  private void budget(List<String> args) {
    String usage = "budget <категория> <сумма> | budget remove <категория>";
    String first = arg(args, 1, usage);
    if (first.equalsIgnoreCase("remove") && args.size() == 3) {
      finance.removeBudget(wallet(), args.get(2));
      out.println("Бюджет удалён.");
      return;
    }
    BigDecimal limit = FinanceService.parseAmount(arg(args, 2, usage));
    finance.setBudget(wallet(), first, limit);
    out.println("Бюджет для «" + first.trim() + "»: " + limit.toPlainString());
    printAlerts(
        finance.allAlerts(wallet()).stream()
            .filter(a -> a.contains("«" + first.trim() + "»"))
            .toList());
  }

  private void budgets() {
    var statuses = finance.budgetStatuses(wallet());
    if (statuses.isEmpty()) {
      out.println("Бюджеты не заданы. Пример: budget Еда 4000");
      return;
    }
    out.printf("%-22s %12s %12s %12s%n", "Категория", "Лимит", "Потрачено", "Остаток");
    statuses.forEach(
        s ->
            out.printf(
                "%-22s %12s %12s %12s%n",
                s.category(),
                ReportService.plain(s.limit()),
                ReportService.plain(s.spent()),
                ReportService.plain(s.remaining())));
  }

  /** stats [категория1,категория2] [--from ГГГГ-ММ-ДД] [--to ГГГГ-ММ-ДД]. */
  private void stats(List<String> args) {
    Set<String> categories = new LinkedHashSet<>();
    LocalDate from = null;
    LocalDate to = null;
    for (int i = 1; i < args.size(); i++) {
      String a = args.get(i);
      if (a.equals("--from")) {
        from = parseDate(arg(args, ++i, "--from ГГГГ-ММ-ДД"));
      } else if (a.equals("--to")) {
        to = parseDate(arg(args, ++i, "--to ГГГГ-ММ-ДД"));
      } else {
        Arrays.stream(a.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(categories::add);
      }
    }
    OperationFilter filter = new OperationFilter(categories, from, to);
    Set<String> unknown = finance.unknownCategories(wallet(), filter);
    if (!unknown.isEmpty()) {
      out.println("Категории не найдены: " + String.join(", ", unknown));
    }
    if (!categories.isEmpty() && unknown.size() == categories.size()) {
      return;
    }
    out.print(reports.summary(wallet(), filter));
  }

  private void export(List<String> args) {
    String usage = "export csv|json <файл>";
    String format = arg(args, 1, usage).toLowerCase();
    Path file = Path.of(arg(args, 2, usage));
    switch (format) {
      case "csv" -> reports.exportCsv(wallet(), file);
      case "json" -> reports.exportJson(wallet(), file);
      default -> throw new ValidationException("Формат должен быть csv или json");
    }
    out.println("Данные выгружены в " + file.toAbsolutePath());
  }

  private void help() {
    out.println(
        """
        Пользователи:
          register <логин> <пароль>        регистрация
          login <логин> <пароль>           вход (кошелёк загружается из файла)
          logout                           выход (кошелёк сохраняется в файл)
        Операции:
          income <сумма> <категория> [комментарий]    добавить доход
          expense <сумма> <категория> [комментарий]   добавить расход
          transfer <логин> <сумма> [комментарий]      перевод другому пользователю
        Бюджеты и категории:
          budget <категория> <сумма>       задать или изменить бюджет
          budget remove <категория>        удалить бюджет
          budgets                          состояние бюджетов
          rename <категория> <новое>       переименовать категорию
          categories                       список категорий
        Отчёты:
          balance | summary | history [N]
          stats [кат1,кат2] [--from ГГГГ-ММ-ДД] [--to ГГГГ-ММ-ДД]
          report <файл>                    сводка в файл
          export csv|json <файл>           выгрузка операций
          import <файл.json>               загрузка операций и бюджетов
        Прочее: help, exit
        Названия из нескольких слов берите в кавычки: expense 3000 "Коммунальные услуги\"""");
  }

  private void printAlerts(List<String> alerts) {
    alerts.forEach(a -> out.println("[!] " + a));
  }

  private Wallet wallet() {
    return session.wallet();
  }

  private static String arg(List<String> args, int index, String usage) {
    if (args.size() <= index || args.get(index).isBlank()) {
      throw new ValidationException("Не хватает аргументов. Использование: " + usage);
    }
    return args.get(index);
  }

  private static String rest(List<String> args, int from) {
    return args.size() > from ? String.join(" ", args.subList(from, args.size())) : "";
  }

  private static LocalDate parseDate(String raw) {
    try {
      return LocalDate.parse(raw);
    } catch (DateTimeParseException e) {
      throw new ValidationException("Дата должна быть в формате ГГГГ-ММ-ДД: " + raw);
    }
  }

  private static int parseCount(String raw) {
    try {
      int n = Integer.parseInt(raw);
      if (n <= 0) {
        throw new NumberFormatException();
      }
      return n;
    } catch (NumberFormatException e) {
      throw new ValidationException("Количество должно быть положительным числом: " + raw);
    }
  }
}
