package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;

/**
 * Seed the initial super-admin. A Java migration is used (not plain SQL) so the
 * password is hashed with the exact same BCrypt encoder the app authenticates
 * against. Idempotent via ON CONFLICT — safe if the email already exists.
 *
 * Credentials: admin@gmail.com / Admin@123  (change the password after first login).
 */
public class V014__seed_superadmin extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String hash = new BCryptPasswordEncoder(12).encode("Admin@123");
        String sql = """
                INSERT INTO users
                    (email, password_hash, full_name, role, status,
                     phone_verified, email_verified, citizenship_verified)
                VALUES (?, ?, 'Super Admin', 'ADMIN', 'VERIFIED', true, true, true)
                ON CONFLICT (email) DO NOTHING
                """;
        try (PreparedStatement ps = context.getConnection().prepareStatement(sql)) {
            ps.setString(1, "admin@gmail.com");
            ps.setString(2, hash);
            ps.executeUpdate();
        }
    }
}
