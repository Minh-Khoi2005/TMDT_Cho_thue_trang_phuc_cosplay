package vn.clarity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import vn.clarity.security.ClarityUserPrincipal;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http,ObjectMapper json) throws Exception {
        http.csrf(Customizer.withDefaults());
        http.requestCache(cache->cache.disable());
        http.sessionManagement(session->session.sessionFixation(fix->fix.changeSessionId()));
        http.authorizeHttpRequests(auth->auth
            .requestMatchers("/error","/pages/access-denied.html").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/health","/api/config","/api/auth/csrf","/api/products","/api/products/**").permitAll()
            .requestMatchers("/api/auth/login","/api/auth/register","/api/auth/resend-verification","/api/auth/verify-email","/api/auth/forgot-password","/api/auth/reset-password","/api/auth/google/**").permitAll()
            .requestMatchers("/api/auth/me").authenticated()
            .requestMatchers("/api/cart","/api/cart/**","/api/orders","/api/orders/**").hasRole("CUSTOMER")
            .requestMatchers("/api/**").denyAll()
            .requestMatchers("/pages/admin-*.html").hasRole("ADMIN")
            .requestMatchers("/pages/staff-*.html","/pages/staff-return-inspection.html").hasAnyRole("STAFF","ADMIN")
            .requestMatchers("/pages/owner-*.html").hasAnyRole("OWNER","ADMIN")
            .requestMatchers("/pages/staff-profile.html").hasAnyRole("STAFF","ADMIN")
            .requestMatchers("/pages/rental-checkout.html","/pages/my-rentals.html","/pages/rental-detail.html").hasRole("CUSTOMER")
            .requestMatchers("/pages/account.html").authenticated()
            .anyRequest().permitAll());
        http.formLogin(form->form.loginPage("/pages/login.html").loginProcessingUrl("/api/auth/login")
            .usernameParameter("identifier").passwordParameter("password")
            .successHandler((req,res,auth)->reply(json,res,200,Map.of("user",((ClarityUserPrincipal)auth.getPrincipal()).view())))
            .failureHandler((req,res,error)-> {
                String code = error instanceof org.springframework.security.authentication.DisabledException ? "EMAIL_NOT_VERIFIED" : "BAD_CREDENTIALS";
                reply(json,res,401,Map.of("code",code,"message",code));
            }));
        http.logout(logout->logout.logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout","POST"))
            .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("CLARITY_SESSION")
            .logoutSuccessHandler((req,res,auth)->reply(json,res,200,Map.of("message","Logged out."))));
        http.exceptionHandling(errors->errors
            .authenticationEntryPoint((req,res,e)-> {
                if(req.getRequestURI().startsWith("/api/")) reply(json,res,401,Map.of("code","LOGIN_REQUIRED","message","Please log in."));
                else res.sendRedirect("/pages/login.html?next="+java.net.URLEncoder.encode(req.getRequestURI()+(req.getQueryString()==null?"":"?"+req.getQueryString()),java.nio.charset.StandardCharsets.UTF_8));
            })
            .accessDeniedHandler((req,res,e)-> {
                if(req.getRequestURI().startsWith("/api/")) reply(json,res,403,Map.of("code",e instanceof org.springframework.security.web.csrf.CsrfException?"CSRF_INVALID":"FORBIDDEN","message","Request denied. Refresh the page and check your account role."));
                else res.sendRedirect("/pages/access-denied.html");
            }));
        http.headers(headers->headers
            .contentSecurityPolicy(csp->csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
            .frameOptions(frame->frame.deny()));
        http.addFilterBefore(new vn.clarity.security.LoginRateLimitFilter(json), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    public static void reply(ObjectMapper mapper,HttpServletResponse res,int status,Object data) throws java.io.IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control","no-store");mapper.writeValue(res.getWriter(),data);
    }
}
