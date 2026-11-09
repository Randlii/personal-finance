package ru.mifi.finance.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import ru.mifi.finance.domain.Wallet;

/** Хранит кошелёк каждого пользователя в отдельном JSON-файле. */
public final class FileWalletRepository implements WalletRepository {

  private final Path dir;

  public FileWalletRepository(Path dir) {
    this.dir = dir;
  }

  @Override
  public Optional<Wallet> load(String login) {
    Path file = fileFor(login);
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          WalletJson.fromJson(Files.readString(file, StandardCharsets.UTF_8), login));
    } catch (IOException e) {
      throw new StorageException("Не удалось прочитать кошелёк " + file, e);
    }
  }

  @Override
  public void save(Wallet wallet) {
    try {
      Files.createDirectories(dir);
      Path tmp = dir.resolve(wallet.owner() + ".json.tmp");
      Files.writeString(tmp, WalletJson.toJson(wallet), StandardCharsets.UTF_8);
      Files.move(
          tmp,
          fileFor(wallet.owner()),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new StorageException("Не удалось сохранить кошелёк " + wallet.owner(), e);
    }
  }

  private Path fileFor(String login) {
    return dir.resolve(login + ".json");
  }
}
