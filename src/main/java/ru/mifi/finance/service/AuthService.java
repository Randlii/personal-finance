package ru.mifi.finance.service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import ru.mifi.finance.domain.User;
import ru.mifi.finance.repository.UserRepository;

/** Регистрация и вход по логину и паролю. Пароли хранятся как PBKDF2-хеш с солью. */
public final class AuthService {

  private static final Pattern LOGIN = Pattern.compile("[A-Za-z0-9_]{3,32}");
  private static final int MIN_PASSWORD = 4;
  private static final int ITERATIONS = 10_000;

  private final UserRepository users;
  private final SecureRandom random = new SecureRandom();

  public AuthService(UserRepository users) {
    this.users = users;
  }

  public User register(String login, String password) {
    if (login == null || !LOGIN.matcher(login).matches()) {
      throw new ValidationException(
          "Логин должен состоять из 3–32 латинских букв, цифр или знаков подчёркивания");
    }
    if (password == null || password.length() < MIN_PASSWORD) {
      throw new ValidationException("Пароль должен быть не короче " + MIN_PASSWORD + " символов");
    }
    if (users.exists(login)) {
      throw new ValidationException("Пользователь " + login + " уже существует");
    }
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    String saltB64 = Base64.getEncoder().encodeToString(salt);
    User user = new User(login, saltB64, hash(password, saltB64));
    users.save(user);
    return user;
  }

  public User login(String login, String password) {
    User user =
        users
            .find(login == null ? "" : login)
            .orElseThrow(() -> new ValidationException("Неверный логин или пароль"));
    if (!hash(password == null ? "" : password, user.salt()).equals(user.passwordHash())) {
      throw new ValidationException("Неверный логин или пароль");
    }
    return user;
  }

  public boolean exists(String login) {
    return users.exists(login);
  }

  private static String hash(String password, String saltB64) {
    try {
      PBEKeySpec spec =
          new PBEKeySpec(
              password.toCharArray(), Base64.getDecoder().decode(saltB64), ITERATIONS, 256);
      byte[] hash =
          SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException(e);
    }
  }
}
