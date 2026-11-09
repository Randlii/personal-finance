package ru.mifi.finance.service;

import java.math.BigDecimal;
import java.util.List;
import ru.mifi.finance.domain.User;
import ru.mifi.finance.domain.Wallet;
import ru.mifi.finance.repository.WalletRepository;

/** Текущая сессия: авторизованный пользователь и его кошелёк. */
public final class Session {

  private final AuthService auth;
  private final WalletRepository wallets;
  private final FinanceService finance;
  private User user;
  private Wallet wallet;

  public Session(AuthService auth, WalletRepository wallets, FinanceService finance) {
    this.auth = auth;
    this.wallets = wallets;
    this.finance = finance;
  }

  public void register(String login, String password) {
    auth.register(login, password);
  }

  /** Вход: при авторизации кошелёк загружается из файла. */
  public void login(String login, String password) {
    if (user != null) {
      logout();
    }
    user = auth.login(login, password);
    wallet = wallets.load(login).orElseGet(() -> new Wallet(login));
  }

  /** Выход: кошелёк сохраняется в файл. */
  public void logout() {
    if (wallet != null) {
      wallets.save(wallet);
    }
    user = null;
    wallet = null;
  }

  public boolean isLoggedIn() {
    return user != null;
  }

  public String login() {
    return requireUser().login();
  }

  public Wallet wallet() {
    requireUser();
    return wallet;
  }

  /** Перевод другому пользователю: оба кошелька сразу сохраняются. */
  public List<String> transfer(String toLogin, BigDecimal amount, String comment) {
    requireUser();
    if (!auth.exists(toLogin)) {
      throw new ValidationException("Пользователь " + toLogin + " не найден");
    }
    if (toLogin.equals(user.login())) {
      throw new ValidationException("Нельзя перевести деньги самому себе");
    }
    Wallet recipient = wallets.load(toLogin).orElseGet(() -> new Wallet(toLogin));
    List<String> alerts = finance.transfer(wallet, recipient, amount, comment);
    wallets.save(recipient);
    wallets.save(wallet);
    return alerts;
  }

  private User requireUser() {
    if (user == null) {
      throw new ValidationException("Сначала войдите: login <логин> <пароль>");
    }
    return user;
  }
}
