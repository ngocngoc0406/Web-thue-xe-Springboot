package com.project.CarRental2.configuration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.project.CarRental2.model.User;
import com.project.CarRental2.service.UserService;
import java.util.List;

@Component
public class RememberMeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RememberMeInterceptor.class);

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        
        // Nếu session chưa đăng nhập, thử hồi phục từ cookie
        if (session.getAttribute("sesionUser") == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("remember_me_user".equals(cookie.getName())) {
                        String username = cookie.getValue();
                        if (username != null && !username.isEmpty()) {
                            try {
                                List<User> list = userService.getAllUserOrderByUsername();
                                for (User u : list) {
                                    if (username.equals(u.getUsername())) {
                                        session.setAttribute("sesionUser", u);
                                        session.setAttribute("sessionRole", u.getRole().getIdRole());
                                        log.info("[RememberMe] Successfully restored session for user: {}", username);
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                log.error("[RememberMe] Error restoring session: {}", e.getMessage());
                            }
                        }
                        break;
                    }
                }
            }
        }
        return true;
    }
}
