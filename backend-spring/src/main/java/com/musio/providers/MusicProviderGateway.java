package com.musio.providers;

import com.musio.model.ProviderType;
import com.musio.providers.observation.ObservedMusicProvider;
import com.musio.providers.observation.ProviderCallObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MusicProviderGateway {
    private final Map<ProviderType, MusicProvider> providers = new EnumMap<>(ProviderType.class);

    public MusicProviderGateway(List<MusicProvider> providerList) {
        this(providerList, null);
    }

    @Autowired
    public MusicProviderGateway(List<MusicProvider> providerList, ProviderCallObserver observer) {
        for (MusicProvider provider : providerList) {
            providers.put(provider.type(), observer == null ? provider : new ObservedMusicProvider(provider, observer));
        }
    }

    public MusicProvider defaultProvider() {
        return provider(ProviderType.QQMUSIC);
    }

    public MusicProvider provider(ProviderType type) {
        MusicProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("Provider is not registered: " + type);
        }
        return provider;
    }
}
