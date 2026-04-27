package com.musio.api;

import com.musio.model.LoginStartResult;
import com.musio.model.LoginState;
import com.musio.model.LoginStatus;
import com.musio.model.ProviderStatus;
import com.musio.model.ProviderType;
import com.musio.providers.ProviderStatusService;
import com.musio.providers.qqmusic.QQMusicAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderStatusService providerStatusService;
    private final QQMusicAuthService qqMusicAuthService;

    public ProviderController(ProviderStatusService providerStatusService, QQMusicAuthService qqMusicAuthService) {
        this.providerStatusService = providerStatusService;
        this.qqMusicAuthService = qqMusicAuthService;
    }

    @GetMapping
    public List<ProviderStatus> providers() {
        return providerStatusService.list();
    }

    @GetMapping("/{provider}/status")
    public ProviderStatus status(@PathVariable String provider) {
        return providerStatusService.status(providerType(provider));
    }

    @PostMapping("/{provider}/login/start")
    public LoginStartResult startLogin(@PathVariable String provider) {
        ProviderType providerType = providerType(provider);
        if (providerType == ProviderType.QQMUSIC) {
            return qqMusicAuthService.startLogin();
        }
        return new LoginStartResult("unavailable", providerType, LoginState.FAILED, null, "Provider login is not available.");
    }

    @GetMapping("/{provider}/login/{sessionId}/status")
    public LoginStatus loginStatus(@PathVariable String provider, @PathVariable String sessionId) {
        ProviderType providerType = providerType(provider);
        if (providerType == ProviderType.QQMUSIC) {
            return qqMusicAuthService.checkLogin(sessionId);
        }
        return new LoginStatus(sessionId, providerType, LoginState.FAILED, false, "Provider login is not available.");
    }

    @PostMapping("/{provider}/logout")
    public LoginStatus logout(@PathVariable String provider) {
        ProviderType providerType = providerType(provider);
        if (providerType == ProviderType.QQMUSIC) {
            return qqMusicAuthService.logout();
        }
        return new LoginStatus("local", providerType, LoginState.LOGGED_OUT, false, "Provider is not connected.");
    }

    private ProviderType providerType(String value) {
        String normalized = value.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        if ("QQ".equals(normalized) || "QQMUSIC".equals(normalized)) {
            return ProviderType.QQMUSIC;
        }
        if ("NETEASE".equals(normalized) || "NETEASECLOUDMUSIC".equals(normalized)) {
            return ProviderType.NETEASE;
        }
        if ("LOCAL".equals(normalized) || "LOCALMUSIC".equals(normalized)) {
            return ProviderType.LOCAL;
        }
        throw new IllegalArgumentException("Unknown provider: " + value);
    }
}
