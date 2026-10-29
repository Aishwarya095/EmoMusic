package com.eis.emomusic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.ViewHolder> {

    private final List<SpotifyTrack> tracks;
    private final OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(SpotifyTrack track);
    }

    public TrackAdapter(List<SpotifyTrack> tracks, OnTrackClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SpotifyTrack track = tracks.get(position);
        holder.tvTrackName.setText(track.name);
        holder.tvArtistName.setText(track.artistName);
        
        Glide.with(holder.itemView.getContext())
                .load(track.albumArtUrl)
                .placeholder(R.color.card_bg_light)
                .into(holder.ivAlbumArt);

        holder.itemView.setOnClickListener(v -> listener.onTrackClick(track));
        holder.btnPlay.setOnClickListener(v -> listener.onTrackClick(track));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAlbumArt;
        TextView tvTrackName;
        TextView tvArtistName;
        ImageView btnPlay;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAlbumArt = itemView.findViewById(R.id.ivAlbumArt);
            tvTrackName = itemView.findViewById(R.id.tvTrackName);
            tvArtistName = itemView.findViewById(R.id.tvArtistName);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }
    }
}
