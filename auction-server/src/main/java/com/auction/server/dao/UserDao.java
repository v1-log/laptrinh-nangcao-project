package com.auction.server.dao;

import com.auction.model.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {
    Optional<User> findById(String id);

    List<User> findAll();

    void save(User user);
}
