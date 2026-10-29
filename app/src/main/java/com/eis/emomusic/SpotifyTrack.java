package com.eis.emomusic;

public class SpotifyTrack {
    public final String id;
    public final String name;
    public final String artistName;
    public final String albumArtUrl;
    public final String uri;

    public SpotifyTrack(String id, String name, String artistName, String albumArtUrl, String uri) {
        this.id = id;
        this.name = name;
        this.artistName = artistName;
        this.albumArtUrl = albumArtUrl;
        this.uri = uri;
    }
}
