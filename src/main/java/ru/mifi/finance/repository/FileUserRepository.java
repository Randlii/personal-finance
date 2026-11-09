package ru.mifi.finance.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.mifi.finance.domain.User;

/** Хранит пользователей в памяти и дублирует их в файл users.json. */
public final class FileUserRepository implements UserRepository {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Type LIST_TYPE = new TypeToken<List<User>>() {}.getType();

  private final Path file;
  private final Map<String, User> users = new LinkedHashMap<>();

  public FileUserRepository(Path file) {
    this.file = file;
    if (Files.exists(file)) {
      try {
        List<User> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_TYPE);
        if (list != null) {
          list.forEach(u -> users.put(u.login(), u));
        }
      } catch (IOException | RuntimeException e) {
        throw new StorageException("Не удалось прочитать " + file, e);
      }
    }
  }

  @Override
  public Optional<User> find(String login) {
    return Optional.ofNullable(users.get(login));
  }

  @Override
  public boolean exists(String login) {
    return users.containsKey(login);
  }

  @Override
  public void save(User user) {
    users.put(user.login(), user);
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      Files.writeString(file, GSON.toJson(new ArrayList<>(users.values())), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new StorageException("Не удалось сохранить пользователей", e);
    }
  }
}
