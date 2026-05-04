package com.musio.api;

import com.musio.config.MusioConfigService;
import com.musio.memory.MusicProfileMemoryStore;
import com.musio.memory.MusicProfileService;
import com.musio.memory.MusicProfileSummaryService;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderControllerMusicGeneTest {
    @TempDir
    Path tempDir;

    @Test
    void musicGeneWritesCurrentSnapshotToAccountScopedStores() {
        MusioConfigService config = config();
        QQMusicGeneStore geneStore = new QQMusicGeneStore(config);
        MusicProfileMemoryStore profileStore = new MusicProfileMemoryStore(config);
        MusicAccountRef accountA = account("10001");
        MusicAccountRef accountB = account("20002");
        geneStore.write(gene(accountA));
        profileStore.write(profile(accountA));

        ProviderController controller = new ProviderController(
                null,
                null,
                sidecar(gene(accountB)),
                geneStore,
                new MusicProfileService(geneStore, profileStore, new MusicProfileSummaryService())
        );

        MusicGeneSnapshot snapshot = controller.musicGene("qqmusic");

        assertEquals(accountB.accountKey(), snapshot.accountKey());
        assertTrue(geneStore.read(accountA).isPresent());
        assertTrue(profileStore.read(accountA).isPresent());
        assertTrue(geneStore.read(accountB).isPresent());
        assertTrue(profileStore.read(accountB).isPresent());
    }

    private QQMusicSidecarClient sidecar(MusicGeneSnapshot snapshot) {
        return new QQMusicSidecarClient(config()) {
            @Override
            public MusicGeneSnapshot musicGene() {
                return snapshot;
            }
        };
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
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private MusioConfigService config() {
        return new MusioConfigService(new MockEnvironment()
                .withProperty("musio.storage.home", tempDir.toString()));
    }
}
