package com.eis.emomusic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public class SpotifyManager {

    private static final String TAG = "SpotifyManager";
    private static final String CLIENT_ID = "8b7701fd5973400bb8187dd893455fbc";
    private static final String REDIRECT_URI = "emomusic://callback";
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;

    private String accessToken;

    private static final Map<String, String> QUERIES = new HashMap<>();
    private static final Map<String, String> OFFICIAL_URIS = new HashMap<>();
    private static final Map<String, String> GENRES = new HashMap<>();

    static {
        QUERIES.put("Angry", "calm meditation relaxing");
        QUERIES.put("Disgust", "soft chill hits");
        QUERIES.put("Fear", "comforting soft pop");
        QUERIES.put("Happy", "happy upbeat pop hits");
        QUERIES.put("Sad", "sad emotional soul");
        QUERIES.put("Surprise", "energetic dance music");
        QUERIES.put("Neutral", "chill jazz background");

        OFFICIAL_URIS.put("Angry", "spotify:playlist:37i9dQZF1DX3oM43CtKnQR");
        OFFICIAL_URIS.put("Disgust", "spotify:playlist:37i9dQZF1DXatMjSclS8o3");
        OFFICIAL_URIS.put("Fear", "spotify:playlist:37i9dQZF1DX7qK8maOR69s");
        OFFICIAL_URIS.put("Happy", "spotify:playlist:37i9dQZF1DXdPec7aLTmlC");
        OFFICIAL_URIS.put("Sad", "spotify:playlist:37i9dQZF1DWZqd5JICZ8MU");
        OFFICIAL_URIS.put("Surprise", "spotify:playlist:37i9dQZF1DX2pSTOxoPbx9");
        OFFICIAL_URIS.put("Neutral", "spotify:playlist:37i9dQZF1DWZeKzbqNYT4q");

        GENRES.put("Angry", "Lo-fi / Calm");
        GENRES.put("Disgust", "Classical / Ambient");
        GENRES.put("Fear", "Soft Pop");
        GENRES.put("Happy", "Pop / Dance");
        GENRES.put("Sad", "R&B / Soul");
        GENRES.put("Surprise", "Electronic");
        GENRES.put("Neutral", "Jazz / Chillhop");
    }

    public interface SpotifyService {
        @GET("v1/search")
        Call<SpotifySearchResponse> searchTracks(
                @Header("Authorization") String auth,
                @Query("q") String query,
                @Query("type") String type,
                @Query("limit") int limit
        );
    }

    private final SpotifyService spotifyService;

    public SpotifyManager() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(logging).build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.spotify.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        spotifyService = retrofit.create(SpotifyService.class);
    }

    public void login(Activity activity) {
        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI);
        builder.setScopes(new String[]{"streaming", "user-read-playback-state", "user-modify-playback-state", "app-remote-control"});
        AuthorizationClient.openLoginActivity(activity, AUTH_TOKEN_REQUEST_CODE, builder.build());
    }

    public boolean handleAuthResponse(int requestCode, int resultCode, Intent intent) {
        if (requestCode == AUTH_TOKEN_REQUEST_CODE) {
            AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthorizationResponse.Type.TOKEN) {
                accessToken = response.getAccessToken();
                return true;
            }
        }
        return false;
    }

    public boolean isConnected() { return accessToken != null; }
    public String getGenreTag(String emotion) { return GENRES.getOrDefault(emotion, ""); }
    public String getOfficialUri(String emotion) { return OFFICIAL_URIS.getOrDefault(emotion, "spotify:playlist:37i9dQZF1DXatMjSclS8o3"); }

    public void searchTracks(String emotion, final TracksCallback callback) {
        if (accessToken == null) { callback.onTracksFound(new ArrayList<>()); return; }
        spotifyService.searchTracks("Bearer " + accessToken, QUERIES.getOrDefault(emotion, "music"), "track", 10)
                .enqueue(new Callback<SpotifySearchResponse>() {
                    @Override
                    public void onResponse(Call<SpotifySearchResponse> call, Response<SpotifySearchResponse> response) {
                        List<SpotifyTrack> tracks = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null && response.body().tracks != null) {
                            for (SpotifySearchResponse.Item item : response.body().tracks.items) {
                                String artUrl = (item.album.images != null && !item.album.images.isEmpty()) ? item.album.images.get(0).url : "";
                                tracks.add(new SpotifyTrack(item.id, item.name, item.artists.get(0).name, artUrl, item.uri));
                            }
                        }
                        callback.onTracksFound(tracks);
                    }
                    @Override
                    public void onFailure(Call<SpotifySearchResponse> call, Throwable t) { callback.onTracksFound(new ArrayList<>()); }
                });
    }

    public void openTrack(Context context, String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.spotify.music");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            String[] parts = uri.split(":");
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/" + parts[1] + "/" + parts[2])));
        }
    }

    public interface TracksCallback { void onTracksFound(List<SpotifyTrack> tracks); }

    public static class SpotifySearchResponse {
        public Tracks tracks;
        public static class Tracks { public List<Item> items; }
        public static class Item {
            public String id;
            public String name;
            public String uri;
            public List<Artist> artists;
            public Album album;
        }
        public static class Artist { public String name; }
        public static class Album { public List<Image> images; }
        public static class Image { public String url; }
    }
}
