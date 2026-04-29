package com.musio.api;

import com.musio.model.LoginStartResult;
import com.musio.model.LoginState;
import com.musio.model.LoginStatus;
import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicProfileMemory;
import com.musio.model.ProviderStatus;
import com.musio.model.ProviderType;
import com.musio.memory.MusicProfileService;
import com.musio.providers.ProviderStatusService;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import com.musio.providers.qqmusic.QQMusicAuthService;
import com.musio.providers.qqmusic.QQMusicSidecarClient;
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
    private final QQMusicSidecarClient qqMusicSidecarClient;
    private final QQMusicGeneStore qqMusicGeneStore;
    private final MusicProfileService musicProfileService;

    public ProviderController(
            ProviderStatusService providerStatusService,
            QQMusicAuthService qqMusicAuthService,
            QQMusicSidecarClient qqMusicSidecarClient,
            QQMusicGeneStore qqMusicGeneStore,
            MusicProfileService musicProfileService
    ) {
        this.providerStatusService = providerStatusService;
        this.qqMusicAuthService = qqMusicAuthService;
        this.qqMusicSidecarClient = qqMusicSidecarClient;
        this.qqMusicGeneStore = qqMusicGeneStore;
        this.musicProfileService = musicProfileService;
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

    @GetMapping("/{provider}/music-gene")
    public MusicGeneSnapshot musicGene(@PathVariable String provider) {
        ProviderType providerType = providerType(provider);
        if (providerType == ProviderType.QQMUSIC) {
            MusicGeneSnapshot snapshot = qqMusicSidecarClient.musicGene();
            qqMusicGeneStore.write(snapshot);
            musicProfileService.writeFromGene(snapshot);
            return snapshot;
        }
        throw new IllegalArgumentException("Provider music gene is not available: " + provider);
    }

    @GetMapping("/{provider}/music-profile")
    public MusicProfileMemory musicProfile(@PathVariable String provider) {
        ProviderType providerType = providerType(provider);
        if (providerType == ProviderType.QQMUSIC) {
            return musicProfileService.readOrCreate()
                    .orElseThrow(() -> new IllegalStateException("Music profile memory has not been generated yet."));
        }
        throw new IllegalArgumentException("Provider music profile is not available: " + provider);
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
