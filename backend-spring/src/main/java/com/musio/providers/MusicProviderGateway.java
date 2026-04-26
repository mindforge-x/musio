package com.musio.providers;

import com.musio.model.ProviderType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MusicProviderGateway {
    private final Map<ProviderType, MusicProvider> providers = new EnumMap<>(ProviderType.class);

    public MusicProviderGateway(List<MusicProvider> providerList) {
        for (MusicProvider provider : providerList) {
            providers.put(provider.type(), provider);
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
