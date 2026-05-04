package com.musio.memory;

import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicProfileMemory;
import com.musio.model.MusicAccountRef;
import com.musio.providers.qqmusic.QQMusicGeneStore;
import com.musio.providers.qqmusic.QQMusicSidecarClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MusicProfileService {
    private final QQMusicGeneStore qqMusicGeneStore;
    private final MusicProfileMemoryStore musicProfileMemoryStore;
    private final MusicProfileSummaryService musicProfileSummaryService;
    private final QQMusicSidecarClient qqMusicSidecarClient;

    @Autowired
    public MusicProfileService(
            QQMusicGeneStore qqMusicGeneStore,
            MusicProfileMemoryStore musicProfileMemoryStore,
            MusicProfileSummaryService musicProfileSummaryService,
            QQMusicSidecarClient qqMusicSidecarClient
    ) {
        this.qqMusicGeneStore = qqMusicGeneStore;
        this.musicProfileMemoryStore = musicProfileMemoryStore;
        this.musicProfileSummaryService = musicProfileSummaryService;
        this.qqMusicSidecarClient = qqMusicSidecarClient;
    }

    public MusicProfileService(
            QQMusicGeneStore qqMusicGeneStore,
            MusicProfileMemoryStore musicProfileMemoryStore,
            MusicProfileSummaryService musicProfileSummaryService
    ) {
        this.qqMusicGeneStore = qqMusicGeneStore;
        this.musicProfileMemoryStore = musicProfileMemoryStore;
        this.musicProfileSummaryService = musicProfileSummaryService;
        this.qqMusicSidecarClient = null;
    }

    public Optional<MusicProfileMemory> readOrCreate() {
        return currentAccount().flatMap(this::readOrCreate);
    }

    public Optional<MusicProfileMemory> readOrCreate(MusicAccountRef account) {
        Optional<MusicProfileMemory> current = musicProfileMemoryStore.read(account);
        if (current.isPresent()) {
            return current;
        }
        return qqMusicGeneStore.read(account).map(this::writeFromGene);
    }

    public MusicProfileMemory writeFromGene(MusicGeneSnapshot snapshot) {
        MusicProfileMemory profile = musicProfileSummaryService.summarize(snapshot);
        musicProfileMemoryStore.write(profile);
        return profile;
    }

    private Optional<MusicAccountRef> currentAccount() {
        if (qqMusicSidecarClient == null) {
            return Optional.empty();
        }
        try {
            QQMusicSidecarClient.QQMusicConnectionStatus connection = qqMusicSidecarClient.connectionStatus();
            return MusicAccountRef.qqMusic(
                    connection.userId(),
                    null,
                    connection.displayName(),
                    connection.authenticated(),
                    connection.checkedAt()
            );
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
