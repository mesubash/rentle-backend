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
 * Credentials: admin@rentle.online / Admin@123  (change the password after first login).
 * This account is also granted SUPER_ADMIN at startup via RENTLE_IAM_SUPER_ADMIN_EMAIL.
 */
public class V014__seed_superadmin extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String hash = new BCryptPasswordEncoder(12).encode("Admin@123");
        String sql = """
                INSERT INTO users
                    (email, password_hash, full_name, role, status,
                     phone_verified, email_verified, citizenship_verified)
                VALUES (?, ?, 'Rentle Admin', 'ADMIN', 'VERIFIED', true, true, true)
                ON CONFLICT (email) DO NOTHING
                """;
        try (PreparedStatement ps = context.getConnection().prepareStatement(sql)) {
            ps.setString(1, "admin@rentle.online");
            ps.setString(2, hash);
            ps.executeUpdate();
        }
    }
}
