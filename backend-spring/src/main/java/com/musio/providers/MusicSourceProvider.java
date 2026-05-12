package com.musio.providers;

import com.musio.model.SourceContext;

import java.util.List;
import java.util.Map;

public interface MusicSourceProvider {
    String sourceId();

    List<SourceCapability> capabilities(SourceContext context);

    Map<String, Object> execute(SourceToolCall call, SourceContext context);
}
