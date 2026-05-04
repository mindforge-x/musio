package com.musio.agent;

import com.musio.model.ProviderType;
import com.musio.model.Song;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeTest {
    @Test
    void formatsOrderedSongListInSongCardOrder() {
        String text = AgentRuntime.orderedSongListText(List.of(
                new Song("qqmusic:1", ProviderType.QQMUSIC, "遇见", List.of("孙燕姿"), "", null, ""),
                new Song("qqmusic:2", ProviderType.QQMUSIC, "一直很安静", List.of("阿桑"), "", null, ""),
                new Song("qqmusic:3", ProviderType.QQMUSIC, "IF YOU", List.of("BIGBANG"), "", null, "")
        ));

        assertTrue(text.indexOf("1. 《遇见》 - 孙燕姿") < text.indexOf("2. 《一直很安静》 - 阿桑"));
        assertTrue(text.indexOf("2. 《一直很安静》 - 阿桑") < text.indexOf("3. 《IF YOU》 - BIGBANG"));
    }
}
