package com.musio.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.config.MusioConfigService;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationHistoryServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsAppendedConversationHistoryFromLocalFile() {
        ConversationHistoryService writer = service();
        Song song = new Song("qqmusic:1", ProviderType.QQMUSIC, "不遗憾", List.of("李荣浩"), "麻雀", 240, "https://example.com/a.jpg");
        writer.appendTurn("local", "给我推荐李荣浩的不遗憾", "我帮你找到了李荣浩的《不遗憾》。", List.of(song));

        ConversationHistoryService reader = service();
        List<ConversationHistoryMessage> messages = reader.load("local");

        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).role());
        assertEquals("给我推荐李荣浩的不遗憾", messages.get(0).content());
        assertEquals("assistant", messages.get(1).role());
        assertEquals("我帮你找到了李荣浩的《不遗憾》。", messages.get(1).content());
        assertEquals(List.of(song), messages.get(1).songs());
        assertEquals(List.of(), messages.get(0).songs());
    }

    @Test
    void loadsLegacyConversationHistoryWithoutSongCards() throws Exception {
        Path conversationsDir = tempDir.resolve("conversations");
        Files.createDirectories(conversationsDir);
        Files.writeString(conversationsDir.resolve("local.jsonl"), """
                {"role":"user","content":"你好","createdAt":"2026-05-03T00:00:00Z"}
                {"role":"assistant","content":"你好呀","createdAt":"2026-05-03T00:00:01Z"}
                """, StandardCharsets.UTF_8);

        List<ConversationHistoryMessage> messages = service().load("local");

        assertEquals(2, messages.size());
        assertEquals("你好", messages.get(0).content());
        assertEquals("你好呀", messages.get(1).content());
        assertEquals(List.of(), messages.get(0).songs());
        assertEquals(List.of(), messages.get(1).songs());
    }

    private ConversationHistoryService service() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("musio.storage.home", tempDir.toString());
        return new ConversationHistoryService(new MusioConfigService(environment), new ObjectMapper());
    }
}
