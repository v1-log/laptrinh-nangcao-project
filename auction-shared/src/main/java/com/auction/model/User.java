package com.auction.model;

public abstract class User {

  private final String id;
  private final String name;
  private final String password;
  private double balance;

  protected User(String id, String name) {
    this(id, name, "", 0.0);
  }

  protected User(String id, String name, String password) {
    this(id, name, password, 0.0);
  }

  protected User(String id, String name, String password, double balance) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("User id is required.");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("User name is required.");
    }
    this.id = id;
    this.name = name;
    this.password = password == null ? "" : password;
    this.balance = balance;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getPassword() {
    return password;
  }

  public double getBalance() {
    return balance;
  }

  public void depositFunds(double amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Deposit amount must be greater than zero.");
    }
    this.balance += amount;
  }

  public void withdrawFunds(double amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
    }
    if (amount > balance) {
      throw new IllegalArgumentException("Insufficient funds.");
    }
    this.balance -= amount;
  }
}
