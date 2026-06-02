package com.auction.server.dao.file;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.server.dao.UserDao;
import com.auction.shared.enums.UserRole;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FileBackedUserDao implements UserDao {
    private static final Type STORED_USERS_TYPE = new TypeToken<List<StoredUser>>() {
    }.getType();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Path storagePath;

    public FileBackedUserDao(Path storagePath) {
        if (storagePath == null) {
            throw new IllegalArgumentException("Storage path is required.");
        }
        this.storagePath = storagePath.toAbsolutePath().normalize();
        loadFromDisk();
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public List<User> findAll() {
        return users.values().stream().toList();
    }

    @Override
    public synchronized void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }
        users.put(user.getId(), user);
        persist();
    }

    private void loadFromDisk() {
        Path sourcePath = storagePath;
        try {
            createParentDirectoryIfNeeded();
            sourcePath = resolveExistingStoragePath();
            if (sourcePath == null) {
                return;
            }

            if (looksLikeJson(sourcePath)) {
                readPersistedUsersFromJson(sourcePath);
            } else {
                try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(sourcePath))) {
                    readPersistedUsers(inputStream.readObject());
                }
            }

            if (!sourcePath.equals(storagePath)) {
                persist();
            }
        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
            recoverFromCorruptStorage(sourcePath, exception);
        }
    }

    private void persist() {
        try {
            createParentDirectoryIfNeeded();
            String payload = GSON.toJson(snapshotUsers(), STORED_USERS_TYPE);
            Files.writeString(
                    storagePath,
                    payload,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save users to " + storagePath, exception);
        }
    }

    private void createParentDirectoryIfNeeded() throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void readPersistedUsersFromJson(Path sourcePath) throws IOException {
        String payload = Files.readString(sourcePath, StandardCharsets.UTF_8);
        if (payload.isBlank()) {
            return;
        }
        List<StoredUser> persistedUsers = GSON.fromJson(payload, STORED_USERS_TYPE);
        if (persistedUsers == null) {
            return;
        }
        for (StoredUser storedUser : persistedUsers) {
            User user = toDomainUser(storedUser);
            users.put(user.getId(), user);
        }
    }

    private void readPersistedUsers(Object payload) {
        if (!(payload instanceof List<?> persistedUsers)) {
            throw new IllegalStateException("Unexpected user storage format in " + storagePath);
        }

        for (Object persistedUser : persistedUsers) {
            if (persistedUser instanceof StoredUser storedUser) {
                User user = toDomainUser(storedUser);
                users.put(user.getId(), user);
                continue;
            }
            if (persistedUser instanceof User user) {
                users.put(user.getId(), user);
                continue;
            }
            throw new IllegalStateException("Invalid user entry found in " + storagePath);
        }
    }

    private List<StoredUser> snapshotUsers() {
        List<StoredUser> snapshot = new ArrayList<>();
        for (User user : users.values()) {
            snapshot.add(toStoredUser(user));
        }
        return snapshot;
    }

    private StoredUser toStoredUser(User user) {
        return new StoredUser(
                user.getId(),
                user.getName(),
                user.getPassword(),
                resolveRole(user),
                user.getBalance());
    }

    private User toDomainUser(StoredUser storedUser) {
        UserRole role = UserRole.valueOf(storedUser.role());
        return switch (role) {
            case BIDDER -> new Bidder(storedUser.id(), storedUser.name(), storedUser.password(), storedUser.balance());
            case SELLER -> new Seller(storedUser.id(), storedUser.name(), storedUser.password(), storedUser.balance());
            case ADMIN -> new Admin(storedUser.id(), storedUser.name(), storedUser.password(), storedUser.balance());
        };
    }

    private String resolveRole(User user) {
        if (user instanceof Seller) {
            return UserRole.SELLER.name();
        }
        if (user instanceof Bidder) {
            return UserRole.BIDDER.name();
        }
        if (user instanceof Admin) {
            return UserRole.ADMIN.name();
        }
        throw new IllegalArgumentException("Unsupported user type: " + user.getClass().getName());
    }

    private Path resolveExistingStoragePath() {
        if (Files.exists(storagePath)) {
            return storagePath;
        }

        String fileName = storagePath.getFileName() == null ? "" : storagePath.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return null;
        }

        Path binarySibling = storagePath.resolveSibling(fileName.substring(0, fileName.length() - 5) + ".bin");
        if (Files.exists(binarySibling)) {
            return binarySibling;
        }

        Path datSibling = storagePath.resolveSibling(fileName.substring(0, fileName.length() - 5) + ".dat");
        if (Files.exists(datSibling)) {
            return datSibling;
        }

        return null;
    }

    private boolean looksLikeJson(Path sourcePath) throws IOException {
        String fileName = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".json")) {
            return true;
        }
        if (fileName.endsWith(".bin") || fileName.endsWith(".dat")) {
            return false;
        }
        try (var reader = Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8)) {
            int nextCharacter;
            while ((nextCharacter = reader.read()) != -1) {
                if (!Character.isWhitespace(nextCharacter)) {
                    return nextCharacter == '[' || nextCharacter == '{';
                }
            }
        }
        return false;
    }

    private void recoverFromCorruptStorage(Path sourcePath, Exception exception) {
        users.clear();
        Path backupPath = backupCorruptStorage(sourcePath, exception);
        System.err.println("Warning: user storage at " + storagePath
                + " was unreadable and has been moved to " + backupPath
                + ". Starting with a fresh user store.");
    }

    private Path backupCorruptStorage(Path sourcePath, Exception originalException) {
        try {
            Path pathToBackup = sourcePath == null ? storagePath : sourcePath;
            Path backupPath = storagePath.resolveSibling(
                    storagePath.getFileName() + ".corrupt-" + System.currentTimeMillis() + ".bak");
            if (Files.exists(pathToBackup)) {
                Files.move(pathToBackup, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return backupPath;
        } catch (IOException backupException) {
            IllegalStateException failure = new IllegalStateException("Unable to load users from " + storagePath, originalException);
            failure.addSuppressed(backupException);
            throw failure;
        }
    }

    private record StoredUser(
            String id,
            String name,
            String password,
            String role,
            double balance) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
