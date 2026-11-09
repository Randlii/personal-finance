package ru.mifi.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mifi.finance.domain.User;
import ru.mifi.finance.repository.FileUserRepository;

class AuthServiceTest {

  @TempDir Path dir;

  @Test
  void registerAndLogin() {
    AuthService auth = new AuthService(new FileUserRepository(dir.resolve("users.json")));
    User u = auth.register("alice", "secret");
    assertNotEquals("secret", u.passwordHash());
    assertEquals("alice", auth.login("alice", "secret").login());
  }

  @Test
  void wrongPasswordAndDuplicates() {
    AuthService auth = new AuthService(new FileUserRepository(dir.resolve("users.json")));
    auth.register("alice", "secret");
    assertThrows(ValidationException.class, () -> auth.login("alice", "wrong"));
    assertThrows(ValidationException.class, () -> auth.login("nobody", "secret"));
    assertThrows(ValidationException.class, () -> auth.register("alice", "other"));
    assertThrows(ValidationException.class, () -> auth.register("a", "secret"));
    assertThrows(ValidationException.class, () -> auth.register("bob", "12"));
  }

  @Test
  void usersPersistBetweenRuns() {
    new AuthService(new FileUserRepository(dir.resolve("users.json"))).register("bob", "pass1");
    AuthService reloaded = new AuthService(new FileUserRepository(dir.resolve("users.json")));
    assertEquals("bob", reloaded.login("bob", "pass1").login());
  }
}
