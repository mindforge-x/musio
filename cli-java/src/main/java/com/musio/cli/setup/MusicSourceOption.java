package com.musio.cli.setup;

public enum MusicSourceOption {
    QQ_MUSIC("qqmusic", "QQ Music", "QR code login", true),
    NETEASE("netease", "NetEase Cloud Music", "Coming soon", false),
    LOCAL("local", "Local Music", "Reserved; requires authorized directories later", false);

    private final String id;
    private final String displayName;
    private final String description;
    private final boolean enabled;

    MusicSourceOption(String id, String displayName, String description, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }
}
