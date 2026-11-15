package com.naghma.accounts;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "display_name", nullable = false)
    public String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Tier tier = Tier.FREE;

    @Column(nullable = false)
    public String locale = "en";

    @Column(nullable = false)
    public boolean admin;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static Optional<User> findByEmail(String email) {
        return find("email", normalize(email)).firstResultOptional();
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
