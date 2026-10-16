package vn.clarity.security;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final JdbcTemplate db;
    public DatabaseUserDetailsService(JdbcTemplate db) { this.db=db; }
    @Override public UserDetails loadUserByUsername(String value) throws UsernameNotFoundException {
        String identifier=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        if(identifier.length()>120 || identifier.isBlank()) throw new UsernameNotFoundException("Invalid credentials");
        var found=db.query("SELECT id,email,phone,display_name,password_hash,role,active FROM app_users WHERE email=? OR phone=? LIMIT 1",
            (rs,n)->new ClarityUserPrincipal(rs.getLong("id"),rs.getString("email"),rs.getString("phone"),
                rs.getString("display_name"),rs.getString("password_hash"),rs.getString("role"),rs.getBoolean("active")),identifier,identifier);
        if(found.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return found.get(0);
    }
}
