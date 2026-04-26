package com.musio.agent;

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

    public boolean requiresConfirmation(String toolName) {
        return CONFIRMATION_REQUIRED.contains(toolName);
    }
}
