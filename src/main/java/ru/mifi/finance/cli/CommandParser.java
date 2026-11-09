package ru.mifi.finance.cli;

import java.util.ArrayList;
import java.util.List;
import ru.mifi.finance.service.ValidationException;

/** Разбивает строку на аргументы с учётом кавычек: income 100 "Коммунальные услуги". */
public final class CommandParser {

  private CommandParser() {}

  public static List<String> split(String line) {
    List<String> args = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    boolean hasToken = false;
    for (char c : line.toCharArray()) {
      if (c == '"') {
        inQuotes = !inQuotes;
        hasToken = true;
      } else if (Character.isWhitespace(c) && !inQuotes) {
        if (hasToken) {
          args.add(current.toString());
          current.setLength(0);
          hasToken = false;
        }
      } else {
        current.append(c);
        hasToken = true;
      }
    }
    if (inQuotes) {
      throw new ValidationException("Не закрыта кавычка");
    }
    if (hasToken) {
      args.add(current.toString());
    }
    return args;
  }
}
