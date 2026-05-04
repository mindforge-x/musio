package com.musio.memory;

import com.musio.config.MusioConfigService;
import com.musio.model.MusicAccountRef;
import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicProfileMemory;
import com.musio.model.ProviderType;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import com.musio.providers.qqmusic.QQMusicSidecarClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicProfileServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void readsOnlyCurrentAccountProfile() {
        MusioConfigService config = config();
        MusicAccountRef accountA = account("10001");
        MusicAccountRef accountB = account("20002");
        MusicProfileMemoryStore profileStore = new MusicProfileMemoryStore(config);
        profileStore.write(profile(accountA));

        MusicProfileService service = new MusicProfileService(
                new QQMusicGeneStore(config),
                profileStore,
                new MusicProfileSummaryService(),
                sidecar(status(accountB.userId()))
        );

        Optional<MusicProfileMemory> profile = service.readOrCreate();

        assertTrue(profile.isEmpty());
    }

    @Test
    void createsProfileFromCurrentAccountGene() {
        MusioConfigService config = config();
        MusicAccountRef account = account("10001");
        QQMusicGeneStore geneStore = new QQMusicGeneStore(config);
        geneStore.write(gene(account));

        MusicProfileService service = new MusicProfileService(
                geneStore,
                new MusicProfileMemoryStore(config),
                new MusicProfileSummaryService(),
                sidecar(status(account.userId()))
        );

        MusicProfileMemory profile = service.readOrCreate().orElseThrow();

        assertEquals(account.accountKey(), profile.accountKey());
        assertEquals(account.userId(), profile.userId());
    }

    private QQMusicSidecarClient sidecar(QQMusicSidecarClient.QQMusicConnectionStatus status) {
        return new QQMusicSidecarClient(config()) {
            @Override
            public QQMusicConnectionStatus connectionStatus() {
                return status;
            }
        };
    }

    private QQMusicSidecarClient.QQMusicConnectionStatus status(String userId) {
        return new QQMusicSidecarClient.QQMusicConnectionStatus(
                "AUTHENTICATED",
                true,
                true,
                userId,
                "User " + userId,
                "ok",
                Instant.EPOCH
        );
    }

    private MusicAccountRef account(String userId) {
        return MusicAccountRef.qqMusic(userId, null, "User " + userId, true, Instant.EPOCH).orElseThrow();
    }

    private MusicGeneSnapshot gene(MusicAccountRef account) {
        return new MusicGeneSnapshot(
                ProviderType.QQMUSIC,
                account.accountKey(),
                account.userId(),
                account.euin(),
                Instant.EPOCH,
                Map.of()
        );
    }

    private MusicProfileMemory profile(MusicAccountRef account) {
        return new MusicProfileMemory(
                ProviderType.QQMUSIC,
                account.accountKey(),
                account.userId(),
                Instant.EPOCH,
                Instant.EPOCH,
                "summary",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                Map.of()
        );
    }

    private MusioConfigService config() {
        return new MusioConfigService(new MockEnvironment()
                .withProperty("musio.storage.home", tempDir.toString()));
    }
}
