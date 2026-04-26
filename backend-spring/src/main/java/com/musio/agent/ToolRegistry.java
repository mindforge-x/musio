package com.musio.agent;

import com.musio.tools.MusicReadTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ToolRegistry {
    private static final Set<String> CONFIRMATION_REQUIRED = Set.of(
            "like_song",
            "add_song_to_playlist",
            "create_playlist",
            "post_comment"
    );

    private final ToolCallback[] readOnlyToolCallbacks;

    public ToolRegistry(MusicReadTools musicReadTools) {
        this.readOnlyToolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(musicReadTools)
                .build()
                .getToolCallbacks();
    }

    public ToolCallback[] readOnlyToolCallbacks() {
        return readOnlyToolCallbacks;
    }

    public boolean requiresConfirmation(String toolName) {
        return CONFIRMATION_REQUIRED.contains(toolName);
    }
}
