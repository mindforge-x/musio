package com.musio.api;

import com.musio.model.LoginStartResult;
import com.musio.model.LoginStatus;
import com.musio.providers.qqmusic.QQMusicAuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/qqmusic")
public class AuthController {
    private final QQMusicAuthService authService;

    public AuthController(QQMusicAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/qr")
    public LoginStartResult createQrLogin() {
        return authService.startLogin();
    }

    @GetMapping("/qr/{sessionId}/status")
    public LoginStatus getQrLoginStatus(@PathVariable String sessionId) {
        return authService.checkLogin(sessionId);
    }

    @DeleteMapping("/logout")
    public LoginStatus logout() {
        return authService.logout();
    }
}
