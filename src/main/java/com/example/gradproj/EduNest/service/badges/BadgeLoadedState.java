package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.exception.ConcurrentModificationConflict;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Hibernate 6.6 SPI boundary. Unavailable loaded snapshots fail closed. */
@Component
final class BadgeLoadedState {
    private final EntityManager entityManager;

    BadgeLoadedState(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    record Values(String title, String description, BadgeCategory category, int points, Long version) { }

    Values requireOriginal(Badge badge) {
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        EntityEntry entry = session.getPersistenceContextInternal().getEntry(badge);
        if (entry == null || entry.isReadOnly() || entry.getLoadedState() == null || !entry.isExistsInDatabase()) {
            throw conflict();
        }
        Object title = entry.getLoadedValue("title");
        Object description = entry.getLoadedValue("description");
        Object category = entry.getLoadedValue("category");
        Object points = entry.getLoadedValue("points");
        Object version = entry.getLoadedValue("version");
        if (!(title instanceof String) || !(description instanceof String)
                || !(category instanceof BadgeCategory) || !(points instanceof Integer) || !(version instanceof Long)) {
            throw conflict();
        }
        return new Values((String) title, (String) description, (BadgeCategory) category,
                (Integer) points, (Long) version);
    }

    void requireUnchanged(Values original, Values authoritative) {
        if (!original.equals(authoritative)) throw conflict();
    }

    void rejectOverlappingChange(Object original, Object current, Object requested) {
        if (requested != null && !Objects.equals(original, current) && !Objects.equals(current, requested)) {
            throw conflict();
        }
    }

    private ConcurrentModificationConflict conflict() {
        return new ConcurrentModificationConflict("Badge changed concurrently or its original state is unavailable");
    }
}
