package com.auction.server.app;

import com.auction.server.dao.file.FileBackedUserDao;

import java.nio.file.Path;

public final class UserStoreJsonConverter {
    private UserStoreJsonConverter() {
    }

    public static void main(String[] args) {
        Path targetPath = resolveTargetPath(args);
        FileBackedUserDao userDao = new FileBackedUserDao(targetPath);
        System.out.println("Converted user store to JSON: " + targetPath.toAbsolutePath().normalize());
        System.out.println("Users loaded: " + userDao.findAll().size());
    }

    private static Path resolveTargetPath(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return normalizeJsonPath(Path.of(args[0].trim()));
        }
        return Path.of("tmp-users.json");
    }

    private static Path normalizeJsonPath(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.endsWith(".bin")) {
            return path.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".json");
        }
        if (fileName.endsWith(".dat")) {
            return path.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".json");
        }
        return path;
    }
}
