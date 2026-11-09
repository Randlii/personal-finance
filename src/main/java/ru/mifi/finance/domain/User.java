package ru.mifi.finance.domain;

/** Пользователь: логин, соль и хеш пароля (пароль в открытом виде не хранится). */
public record User(String login, String salt, String passwordHash) {}
