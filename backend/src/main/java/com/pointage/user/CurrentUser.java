package com.pointage.user;

import com.pointage.auth.CustomUserDetailsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public String getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Not authenticated");
        }
        if (auth.getPrincipal() instanceof UserDetails ud) {
            return ud.getUsername();
        }
        return auth.getName();
    }
}
