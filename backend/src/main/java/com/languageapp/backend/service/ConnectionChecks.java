package com.languageapp.backend.service;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.ForbiddenException;

/** Shared by code-based and link-based connection operations. */
public final class ConnectionChecks {
    private ConnectionChecks() { }
    public static void requireVerified(User user) {
        if (user == null || !user.isActive() || user.getRole() == Role.DELETED)
            throw new ForbiddenException("Ez a fiók nem használható kapcsolat létrehozására.");
        if (!user.isVerified())
            throw new ForbiddenException("Előbb erősítsd meg az e-mail címedet!");
    }
    public static void requireAvailableTarget(User user) {
        if (user == null || !user.isActive() || !user.isVerified() || user.getRole() == Role.DELETED)
            throw new com.languageapp.backend.exception.BadRequestException("A kiválasztott felhasználó jelenleg nem elérhető.");
    }
    public static void requireStudent(User user) {
        requireVerified(user);
        if (user.getRole() != Role.STUDENT)
            throw new ForbiddenException("Osztályteremhez diákként lehet csatlakozni.");
    }
}
