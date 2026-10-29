package com.eis.emomusic;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.List;

public class MusicBottomSheet extends BottomSheetDialogFragment {

    private final String emotion;
    private final String genre;
    private final List<SpotifyTrack> tracks;
    private final SpotifyManager spotifyManager;

    public MusicBottomSheet(String emotion, String genre, List<SpotifyTrack> tracks, SpotifyManager spotifyManager) {
        this.emotion = emotion;
        this.genre = genre;
        this.tracks = tracks;
        this.spotifyManager = spotifyManager;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottom_sheet_music, container, false);

        TextView tvTitle = v.findViewById(R.id.tvSheetTitle);
        Chip chipGenre = v.findViewById(R.id.chipGenre);
        RecyclerView rvTracks = v.findViewById(R.id.rvTracks);
        Button btnOpenSpotify = v.findViewById(R.id.btnOpenSpotify);

        tvTitle.setText(getString(R.string.music_for_your, emotion));
        chipGenre.setText(genre);

        rvTracks.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTracks.setAdapter(new TrackAdapter(tracks, track -> {
            spotifyManager.openTrack(requireContext(), track.uri);
        }));

        btnOpenSpotify.setOnClickListener(view -> {
            if (!tracks.isEmpty()) {
                spotifyManager.openTrack(requireContext(), tracks.get(0).uri);
            }
        });

        return v;
    }

    @Override
    public int getTheme() {
        return R.style.Theme_EmoMusic_BottomSheetDialog;
    }
}
