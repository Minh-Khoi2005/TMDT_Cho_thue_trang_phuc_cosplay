package vn.clarity.security;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.clarity.model.Models.UserView;
import java.util.*;
public final class ClarityUserPrincipal implements UserDetails {
    private final long id;
    private final String email,phone,name,password,role;
    private final boolean enabled;
    public ClarityUserPrincipal(long id,String email,String phone,String name,String password,String role,boolean enabled) {
        this.id=id;this.email=email;this.phone=phone;this.name=name;this.password=password;this.role=role;this.enabled=enabled;
    }
    public long id() { return id; }
    public String role() { return role; }
    public UserView view() { return new UserView(id,name,email,phone,role); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_"+role)); }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
