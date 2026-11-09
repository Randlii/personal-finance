package ru.mifi.finance.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.mifi.finance.domain.Operation;
import ru.mifi.finance.domain.OperationType;
import ru.mifi.finance.domain.Wallet;

/** Преобразование кошелька в JSON и обратно. */
public final class WalletJson {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private WalletJson() {}

  public static String toJson(Wallet wallet) {
    WalletDto dto = new WalletDto();
    dto.owner = wallet.owner();
    dto.operations = new ArrayList<>();
    for (Operation op : wallet.operations()) {
      OperationDto o = new OperationDto();
      o.type = op.type().name();
      o.category = op.category();
      o.amount = op.amount().toPlainString();
      o.time = op.time().toString();
      o.comment = op.comment();
      dto.operations.add(o);
    }
    dto.budgets = new LinkedHashMap<>();
    wallet.budgets().forEach((k, v) -> dto.budgets.put(k, v.toPlainString()));
    return GSON.toJson(dto);
  }

  /** Читает кошелёк из JSON. Если ownerOverride не null, владелец заменяется на него. */
  public static Wallet fromJson(String json, String ownerOverride) {
    try {
      WalletDto dto = GSON.fromJson(json, WalletDto.class);
      if (dto == null) {
        throw new IllegalArgumentException("Пустой файл");
      }
      String owner = ownerOverride != null ? ownerOverride : dto.owner;
      if (owner == null) {
        throw new IllegalArgumentException("В файле не указан владелец кошелька");
      }
      Wallet wallet = new Wallet(owner);
      if (dto.operations != null) {
        for (OperationDto o : dto.operations) {
          wallet.add(
              new Operation(
                  OperationType.valueOf(o.type),
                  o.category,
                  new BigDecimal(o.amount),
                  LocalDateTime.parse(o.time),
                  o.comment));
        }
      }
      if (dto.budgets != null) {
        dto.budgets.forEach((k, v) -> wallet.setBudget(k, new BigDecimal(v)));
      }
      return wallet;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Некорректный формат данных кошелька: " + e.getMessage(), e);
    }
  }

  private static final class WalletDto {
    String owner;
    List<OperationDto> operations;
    Map<String, String> budgets;
  }

  private static final class OperationDto {
    String type;
    String category;
    String amount;
    String time;
    String comment;
  }
}
