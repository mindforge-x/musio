package com.musio.providers;

import com.musio.model.ProviderStatus;
import com.musio.model.ProviderType;
import com.musio.providers.qqmusic.QQMusicCredentialStore;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import com.musio.providers.qqmusic.QQMusicSidecarClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderStatusService {
    private final QQMusicCredentialStore qqMusicCredentialStore;
    private final QQMusicSidecarClient qqMusicSidecarClient;
    private final QQMusicGeneStore qqMusicGeneStore;

    public ProviderStatusService(
            QQMusicCredentialStore qqMusicCredentialStore,
            QQMusicSidecarClient qqMusicSidecarClient,
            QQMusicGeneStore qqMusicGeneStore
    ) {
        this.qqMusicCredentialStore = qqMusicCredentialStore;
        this.qqMusicSidecarClient = qqMusicSidecarClient;
        this.qqMusicGeneStore = qqMusicGeneStore;
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
            case QQMUSIC -> qqMusicStatus();
            case NETEASE -> new ProviderStatus(
                    provider,
                    "网易云音乐",
                    false,
                    false,
                    false,
                    "COMING_SOON",
                    "网易云音乐 Provider 已预留，后续版本接入。",
                    "COMING_SOON",
                    "UNAVAILABLE"
            );
            case LOCAL -> new ProviderStatus(
                    provider,
                    "本地音乐",
                    false,
                    false,
                    false,
                    "AUTHORIZED_DIRECTORY",
                    "本地音乐已预留，未来必须通过授权目录沙箱访问。",
                    "SANDBOX_RESERVED",
                    "UNAVAILABLE"
            );
        };
    }

    private ProviderStatus qqMusicStatus() {
        boolean credentialStored = qqMusicCredentialStore.exists();
        if (!credentialStored) {
            return new ProviderStatus(
                    ProviderType.QQMUSIC,
                    "QQ 音乐",
                    true,
                    false,
                    false,
                    "QR_CODE",
                    "需要扫码登录 QQ 音乐。",
                    "NOT_LOGGED_IN",
                    "UNAVAILABLE"
            );
        }

        try {
            QQMusicSidecarClient.QQMusicConnectionStatus connection = qqMusicSidecarClient.connectionStatus();
            boolean authenticated = connection.authenticated();
            String musicGeneState = authenticated
                    ? (qqMusicGeneStore.exists() ? "READY" : "MISSING")
                    : "UNAVAILABLE";
            return new ProviderStatus(
                    ProviderType.QQMUSIC,
                    "QQ 音乐",
                    true,
                    authenticated,
                    connection.credentialStored(),
                    "QR_CODE",
                    qqMusicMessage(connection.state(), authenticated),
                    connection.state(),
                    musicGeneState
            );
        } catch (RuntimeException e) {
            return new ProviderStatus(
                    ProviderType.QQMUSIC,
                    "QQ 音乐",
                    true,
                    false,
                    true,
                    "QR_CODE",
                    "QQ 音乐凭证存在，但当前无法完成远端校验。请确认 sidecar 已启动。",
                    "UNVERIFIED",
                    qqMusicGeneStore.exists() ? "READY" : "UNKNOWN"
            );
        }
    }

    private String qqMusicMessage(String state, boolean authenticated) {
        if (authenticated) {
            return qqMusicGeneStore.exists()
                    ? "QQ 音乐已连接，音乐基因已生成。"
                    : "QQ 音乐已连接，音乐基因待生成。";
        }
        return switch (state) {
            case "NOT_LOGGED_IN" -> "需要扫码登录 QQ 音乐。";
            case "EXPIRED" -> "QQ 音乐登录已过期，请重新扫码。";
            default -> "QQ 音乐暂未连接。";
        };
    }
}
