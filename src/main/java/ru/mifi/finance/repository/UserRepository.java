package ru.mifi.finance.repository;

import java.util.Optional;
import ru.mifi.finance.domain.User;

/** Хранилище пользователей. */
public interface UserRepository {
  Optional<User> find(String login);

  boolean exists(String login);

  void save(User user);
}
