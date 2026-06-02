package com.auction.server.dao.memory;

import com.auction.model.User;
import com.auction.server.dao.UserDao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserDao implements UserDao {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public List<User> findAll() {
        return users.values().stream().toList();
    }

    @Override
    public void save(User user) {
        users.put(user.getId(), user);
    }
}
