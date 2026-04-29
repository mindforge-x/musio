package com.musio.memory;

import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicProfileMemory;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MusicProfileService {
    private final QQMusicGeneStore qqMusicGeneStore;
    private final MusicProfileMemoryStore musicProfileMemoryStore;
    private final MusicProfileSummaryService musicProfileSummaryService;

    public MusicProfileService(
            QQMusicGeneStore qqMusicGeneStore,
            MusicProfileMemoryStore musicProfileMemoryStore,
            MusicProfileSummaryService musicProfileSummaryService
    ) {
        this.qqMusicGeneStore = qqMusicGeneStore;
        this.musicProfileMemoryStore = musicProfileMemoryStore;
        this.musicProfileSummaryService = musicProfileSummaryService;
    }

    public Optional<MusicProfileMemory> readOrCreate() {
        Optional<MusicProfileMemory> current = musicProfileMemoryStore.read();
        if (current.isPresent()) {
            return current;
        }
        return qqMusicGeneStore.read().map(this::writeFromGene);
    }

    public MusicProfileMemory writeFromGene(MusicGeneSnapshot snapshot) {
        MusicProfileMemory profile = musicProfileSummaryService.summarize(snapshot);
        musicProfileMemoryStore.write(profile);
        return profile;
    }
}
