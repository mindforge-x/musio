package com.musio.providers.qqmusic;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.musio.model.LoginStartResult;
import com.musio.model.LoginState;
import com.musio.model.LoginStatus;
import com.musio.model.ProviderType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class QQMusicAuthService {
    private final QQMusicCredentialStore credentialStore;
    private final Cache<String, LoginState> loginStates = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(1000)
            .build();

    public QQMusicAuthService(QQMusicCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    public LoginStartResult startLogin() {
        String sessionId = UUID.randomUUID().toString();
        loginStates.put(sessionId, LoginState.CREATED);

        return new LoginStartResult(
                sessionId,
                ProviderType.QQMUSIC,
                LoginState.CREATED,
                null,
                "QR login service is ready for migration from aasee-music-backend."
        );
    }

    public LoginStatus checkLogin(String sessionId) {
        LoginState state = loginStates.getIfPresent(sessionId);
        if (state == null) {
            state = LoginState.EXPIRED;
        }

        return new LoginStatus(
                sessionId,
                ProviderType.QQMUSIC,
                state,
                credentialStore.exists(),
                "QQ Music QR status polling is not wired yet."
        );
    }

    public LoginStatus logout() {
        credentialStore.delete();
        return new LoginStatus("local", ProviderType.QQMUSIC, LoginState.LOGGED_OUT, false, "Logged out.");
    }
}
