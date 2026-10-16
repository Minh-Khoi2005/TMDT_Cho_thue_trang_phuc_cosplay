package vn.clarity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** In-memory rate limit for authentication requests. */
public final class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW=60_000L;
    private final Map<String,Window> attempts=new LinkedHashMap<>();
    private record Window(long started,int count) {}
    private final ObjectMapper json;
    public LoginRateLimitFilter(ObjectMapper json) { this.json=json; }
    private synchronized boolean allowed(String ip) {
        long now=System.currentTimeMillis();
        attempts.entrySet().removeIf(e->now-e.getValue().started()>WINDOW);
        if(attempts.size()>2048) attempts.remove(attempts.keySet().iterator().next());
        Window w=attempts.get(ip);
        if(w==null) {attempts.put(ip,new Window(now,1));return true;}
        if(w.count()>=20) return false;
        attempts.put(ip,new Window(w.started(),w.count()+1));return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getServletPath();
        if("POST".equals(req.getMethod()) && path.startsWith("/api/auth/") && !"/api/auth/logout".equals(path)) {
            if(!allowed(req.getRemoteAddr())) {
                res.setHeader("Retry-After","60");
                vn.clarity.config.SecurityConfig.reply(json,res,429,Map.of("code","TOO_MANY_ATTEMPTS","message","Please wait before trying again."));return;
            }
        }
        if("POST".equals(req.getMethod()) && "/api/auth/login".equals(path)) {
            String id=req.getParameter("identifier"),password=req.getParameter("password");
            if(id==null || id.isBlank() || id.length()>120 || password==null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length>72) {
                vn.clarity.config.SecurityConfig.reply(json,res,400,Map.of("code","INVALID_LOGIN","message","Enter a valid account and password (up to 72 UTF-8 bytes)."));return;
            }
        }
        chain.doFilter(req,res);
    }
}
