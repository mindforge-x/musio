package com.musio.providers;

import com.musio.memory.MusicProfileMemoryStore;
import com.musio.model.MusicAccountRef;
import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicGeneState;
import com.musio.model.MusicGeneStatus;
import com.musio.model.MusicProfileMemory;
import com.musio.model.ProviderStatus;
import com.musio.model.ProviderType;
import com.musio.providers.qqmusic.QQMusicCredentialStore;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import com.musio.providers.qqmusic.QQMusicSidecarClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProviderStatusService {
    private final QQMusicCredentialStore qqMusicCredentialStore;
    private final QQMusicSidecarClient qqMusicSidecarClient;
    private final QQMusicGeneStore qqMusicGeneStore;
    private final MusicProfileMemoryStore musicProfileMemoryStore;

    public ProviderStatusService(
            QQMusicCredentialStore qqMusicCredentialStore,
            QQMusicSidecarClient qqMusicSidecarClient,
            QQMusicGeneStore qqMusicGeneStore,
            MusicProfileMemoryStore musicProfileMemoryStore
    ) {
        this.qqMusicCredentialStore = qqMusicCredentialStore;
        this.qqMusicSidecarClient = qqMusicSidecarClient;
        this.qqMusicGeneStore = qqMusicGeneStore;
        this.musicProfileMemoryStore = musicProfileMemoryStore;
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
                    "UNAVAILABLE",
                    MusicGeneStatus.unavailable(provider, "网易云音乐 Provider 已预留，后续版本接入。")
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
                    "UNAVAILABLE",
                    MusicGeneStatus.unavailable(provider, "本地音乐已预留，未来必须通过授权目录沙箱访问。")
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
                    "UNAVAILABLE",
                    MusicGeneStatus.unavailable(ProviderType.QQMUSIC, "需要扫码登录 QQ 音乐。")
            );
        }

        try {
            QQMusicSidecarClient.QQMusicConnectionStatus connection = qqMusicSidecarClient.connectionStatus();
            boolean authenticated = connection.authenticated();
            MusicGeneStatus musicGeneStatus = qqMusicStatus(connection);
            return new ProviderStatus(
                    ProviderType.QQMUSIC,
                    "QQ 音乐",
                    true,
                    authenticated,
                    connection.credentialStored(),
                    "QR_CODE",
                    qqMusicMessage(connection.state(), authenticated),
                    connection.state(),
                    musicGeneStatus.state().name(),
                    musicGeneStatus
            );
        } catch (RuntimeException e) {
            MusicGeneStatus musicGeneStatus = unavailableToVerifyStatus();
            return new ProviderStatus(
                    ProviderType.QQMUSIC,
                    "QQ 音乐",
                    true,
                    false,
                    true,
                    "QR_CODE",
                    "QQ 音乐凭证存在，但当前无法完成远端校验。请确认 sidecar 已启动。",
                    "UNVERIFIED",
                    musicGeneStatus.state().name(),
                    musicGeneStatus
            );
        }
    }

    private String qqMusicMessage(String state, boolean authenticated) {
        if (authenticated) {
            return "QQ 音乐已连接。";
        }
        return switch (state) {
            case "NOT_LOGGED_IN" -> "需要扫码登录 QQ 音乐。";
            case "EXPIRED" -> "QQ 音乐登录已过期，请重新扫码。";
            default -> "QQ 音乐暂未连接。";
        };
    }

    private MusicGeneStatus qqMusicStatus(QQMusicSidecarClient.QQMusicConnectionStatus connection) {
        if (!connection.authenticated()) {
            return MusicGeneStatus.unavailable(ProviderType.QQMUSIC, "QQ 音乐暂未连接。");
        }
        Optional<MusicAccountRef> account = MusicAccountRef.qqMusic(
                connection.userId(),
                null,
                connection.displayName(),
                connection.authenticated(),
                connection.checkedAt()
        );
        if (account.isEmpty()) {
            return new MusicGeneStatus(
                    MusicGeneState.UNVERIFIED,
                    ProviderType.QQMUSIC,
                    null,
                    connection.userId(),
                    null,
                    null,
                    null,
                    null,
                    false,
                    "sidecar_unverified",
                    "QQ 音乐已连接，但当前无法解析账号身份。请重新校验登录。"
            );
        }
        return accountStatus(account.get());
    }

    private MusicGeneStatus accountStatus(MusicAccountRef account) {
        Optional<MusicGeneSnapshot> gene = qqMusicGeneStore.read(account);
        Optional<MusicProfileMemory> profile = musicProfileMemoryStore.read(account);
        if (profile.isEmpty()) {
            return new MusicGeneStatus(
                    MusicGeneState.MISSING,
                    account.provider(),
                    account.accountKey(),
                    account.userId(),
                    account.euin(),
                    gene.map(MusicGeneSnapshot::generatedAt).orElse(null),
                    null,
                    null,
                    false,
                    "profile_missing",
                    "QQ 音乐已连接，可以为当前账号生成音乐基因。"
            );
        }
        MusicProfileMemory currentProfile = profile.get();
        boolean profileSynced = gene
                .map(snapshot -> snapshot.generatedAt().equals(currentProfile.sourceGeneGeneratedAt()))
                .orElse(false);
        return new MusicGeneStatus(
                MusicGeneState.READY,
                account.provider(),
                account.accountKey(),
                account.userId(),
                account.euin(),
                gene.map(MusicGeneSnapshot::generatedAt).orElse(null),
                currentProfile.generatedAt(),
                currentProfile.sourceGeneGeneratedAt(),
                profileSynced,
                null,
                "QQ 音乐已连接，当前账号音乐基因已生成。"
        );
    }

    private MusicGeneStatus unavailableToVerifyStatus() {
        return new MusicGeneStatus(
                MusicGeneState.UNVERIFIED,
                ProviderType.QQMUSIC,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "sidecar_unverified",
                "QQ 音乐凭证存在，但当前无法完成远端校验。请确认 sidecar 已启动。"
        );
    }
}
