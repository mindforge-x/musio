package com.musio.providers;

import com.musio.model.ProviderStatus;
import com.musio.model.ProviderType;
import com.musio.providers.qqmusic.QQMusicCredentialStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderStatusService {
    private final QQMusicCredentialStore qqMusicCredentialStore;

    public ProviderStatusService(QQMusicCredentialStore qqMusicCredentialStore) {
        this.qqMusicCredentialStore = qqMusicCredentialStore;
    }

    public List<ProviderStatus> list() {
        return List.of(
                status(ProviderType.QQMUSIC),
                status(ProviderType.NETEASE),
                status(ProviderType.LOCAL)
        );
    }

    public ProviderStatus status(ProviderType provider) {
        return switch (provider) {
            case QQMUSIC -> new ProviderStatus(
                    provider,
                    "QQ Music",
                    true,
                    qqMusicCredentialStore.exists(),
                    "QR_CODE",
                    qqMusicCredentialStore.exists() ? "Credential is stored." : "QR login is required."
            );
            case NETEASE -> new ProviderStatus(
                    provider,
                    "NetEase Cloud Music",
                    false,
                    false,
                    "COMING_SOON",
                    "NetEase provider is reserved for a later release."
            );
            case LOCAL -> new ProviderStatus(
                    provider,
                    "Local Music",
                    false,
                    false,
                    "AUTHORIZED_DIRECTORY",
                    "Local music is reserved; future access must use authorized directories."
            );
        };
    }
}
