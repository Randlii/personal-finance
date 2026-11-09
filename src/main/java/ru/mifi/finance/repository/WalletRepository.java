package ru.mifi.finance.repository;

import java.util.Optional;
import ru.mifi.finance.domain.Wallet;

/** Хранилище кошельков. */
public interface WalletRepository {
  Optional<Wallet> load(String login);

  void save(Wallet wallet);
}
