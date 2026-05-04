package com.musio.providers;

import com.musio.config.MusioConfigService;
import com.musio.memory.MusicProfileMemoryStore;
import com.musio.model.MusicAccountRef;
import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicGeneState;
import com.musio.model.MusicProfileMemory;
import com.musio.model.ProviderStatus;
import com.musio.model.ProviderType;
import com.musio.providers.qqmusic.QQMusicCredentialStore;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import com.musio.providers.qqmusic.QQMusicSidecarClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderStatusServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsReadyForCurrentAccountProfile() {
        MusioConfigService config = config();
        MusicAccountRef account = account("10001");
        stores(config).profileStore().write(profile(account));

        ProviderStatus status = service(config, true, sidecar(status("10001"))).status(ProviderType.QQMUSIC);

        assertEquals("READY", status.musicGeneState());
        assertEquals(MusicGeneState.READY, status.musicGeneStatus().state());
        assertEquals(account.accountKey(), status.musicGeneStatus().accountKey());
    }

    @Test
    void doesNotUseAnotherAccountProfileForCurrentAccount() {
        MusioConfigService config = config();
        StoreSet stores = stores(config);
        stores.profileStore().write(profile(account("10001")));

        ProviderStatus status = service(config, true, sidecar(status("20002"))).status(ProviderType.QQMUSIC);

        assertEquals("MISSING", status.musicGeneState());
        assertEquals(MusicGeneState.MISSING, status.musicGeneStatus().state());
        assertEquals(MusicAccountRef.accountKey(ProviderType.QQMUSIC, "20002"), status.musicGeneStatus().accountKey());
    }

    @Test
    void ignoresLegacyGlobalProfileWhenAccountProfileIsMissing() throws Exception {
        MusioConfigService config = config();
        Files.createDirectories(tempDir.resolve("memory"));
        Files.writeString(tempDir.resolve("memory").resolve("music-profile.json"), "{}");

        ProviderStatus status = service(config, true, sidecar(status("20002"))).status(ProviderType.QQMUSIC);

        assertEquals("MISSING", status.musicGeneState());
        assertEquals(MusicGeneState.MISSING, status.musicGeneStatus().state());
    }

    @Test
    void returnsUnverifiedWhenSidecarCannotValidateCredential() {
        MusioConfigService config = config();

        ProviderStatus status = service(config, true, sidecarFailure()).status(ProviderType.QQMUSIC);

        assertEquals("UNVERIFIED", status.musicGeneState());
        assertEquals(MusicGeneState.UNVERIFIED, status.musicGeneStatus().state());
    }

    @Test
    void returnsUnverifiedWhenAuthenticatedAccountHasNoUserId() {
        MusioConfigService config = config();

        ProviderStatus status = service(config, true, sidecar(status(null))).status(ProviderType.QQMUSIC);

        assertEquals("UNVERIFIED", status.musicGeneState());
        assertEquals(MusicGeneState.UNVERIFIED, status.musicGeneStatus().state());
    }

    @Test
    void storesGeneAndProfileByAccountKey() {
        MusioConfigService config = config();
        StoreSet stores = stores(config);
        MusicAccountRef accountA = account("10001");
        MusicAccountRef accountB = account("20002");

        stores.geneStore().write(gene(accountA));
        stores.profileStore().write(profile(accountA));
        stores.geneStore().write(gene(accountB));
        stores.profileStore().write(profile(accountB));

        assertTrue(stores.geneStore().read(accountA).isPresent());
        assertTrue(stores.profileStore().read(accountA).isPresent());
        assertTrue(stores.geneStore().read(accountB).isPresent());
        assertTrue(stores.profileStore().read(accountB).isPresent());
        assertFalse(stores.geneStore().read(account("30003")).isPresent());
    }

    @Test
    void rejectsUnsafeAccountKeyWhenWritingGene() {
        StoreSet stores = stores(config());

        MusicGeneSnapshot unsafeSnapshot = new MusicGeneSnapshot(
                ProviderType.QQMUSIC,
                "../qqmusic_10001",
                "10001",
                null,
                Instant.EPOCH,
                Map.of()
        );

        assertThrows(IllegalArgumentException.class, () -> stores.geneStore().write(unsafeSnapshot));
    }

    @Test
    void rejectsUnsafeAccountKeyWhenWritingProfile() {
        StoreSet stores = stores(config());

        MusicProfileMemory unsafeProfile = new MusicProfileMemory(
                ProviderType.QQMUSIC,
                "../qqmusic_10001",
                "10001",
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

        assertThrows(IllegalArgumentException.class, () -> stores.profileStore().write(unsafeProfile));
    }

    private ProviderStatusService service(MusioConfigService config, boolean credentialStored, QQMusicSidecarClient sidecarClient) {
        StoreSet stores = stores(config);
        return new ProviderStatusService(
                credentialStore(config, credentialStored),
                sidecarClient,
                stores.geneStore(),
                stores.profileStore()
        );
    }

    private StoreSet stores(MusioConfigService config) {
        return new StoreSet(new QQMusicGeneStore(config), new MusicProfileMemoryStore(config));
    }

    private QQMusicCredentialStore credentialStore(MusioConfigService config, boolean exists) {
        return new QQMusicCredentialStore(config) {
            @Override
            public boolean exists() {
                return exists;
            }
        };
    }

    private QQMusicSidecarClient sidecar(QQMusicSidecarClient.QQMusicConnectionStatus status) {
        return new QQMusicSidecarClient(config()) {
            @Override
            public QQMusicConnectionStatus connectionStatus() {
                return status;
            }
        };
    }

    private QQMusicSidecarClient sidecarFailure() {
        return new QQMusicSidecarClient(config()) {
            @Override
            public QQMusicConnectionStatus connectionStatus() {
                throw new IllegalStateException("sidecar unavailable");
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

    private record StoreSet(QQMusicGeneStore geneStore, MusicProfileMemoryStore profileStore) {
    }
}
