package com.musio.memory;

import com.musio.config.MusioConfigService;
import com.musio.model.MusicAccountRef;
import com.musio.model.MusicProfileMemory;
import com.musio.model.ProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicProfilePreferenceUpdaterTest {
    @TempDir
    Path tempDir;

    @Test
    void aggregatesPreferenceCandidatesIntoLongTermMusicProfile() {
        MusioConfigService config = new MusioConfigService(new MockEnvironment()
                .withProperty("musio.storage.home", tempDir.resolve("home").toString()));
        MusicProfileMemoryStore profileStore = new MusicProfileMemoryStore(config);
        MusicAccountRef account = MusicAccountRef.qqMusic("10001", null, "User 10001", true, Instant.EPOCH).orElseThrow();
        profileStore.write(profile(account));
        PreferenceStore preferenceStore = new PreferenceStore(new SQLiteMemoryDatabase(tempDir.resolve("memory.sqlite")));
        Instant now = Instant.now().minusSeconds(60);
        preferenceStore.addCandidate(new PreferenceCandidate(
                "",
                "local",
                "negative",
                "too_noisy",
                "不想听太吵的歌",
                0.15,
                "用户说今天有点累，别太吵",
                "session_feedback",
                now
        ));
        preferenceStore.addCandidate(new PreferenceCandidate(
                "",
                "local",
                "positive",
                "quiet_focus",
                "安静专注的歌",
                0.25,
                "用户说这个不错，多来点",
                "explicit_feedback",
                now.plusSeconds(1)
        ));
        MusicProfilePreferenceUpdater updater = new MusicProfilePreferenceUpdater(
                new PreferenceAggregator(preferenceStore),
                preferenceStore,
                profileStore
        );

        int updated = updater.refreshAll();

        MusicProfileMemory updatedProfile = profileStore.read(account).orElseThrow();
        assertEquals(1, updated);
        assertTrue(updatedProfile.summary().contains("本地动态偏好"));
        assertTrue(updatedProfile.strongPreferences().stream().anyMatch(value -> value.contains("安静专注的歌")));
        assertTrue(updatedProfile.recommendationHints().stream().anyMatch(value -> value.contains("安静专注的歌")));
        assertTrue(updatedProfile.avoid().stream().anyMatch(value -> value.contains("不想听太吵的歌")));
        assertEquals(2, updatedProfile.sourceStats().get("localPreferenceItemCount"));
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
                List.of("用户当前明确指令优先级高于长期画像。"),
                List.of(),
                Map.of()
        );
    }
}
