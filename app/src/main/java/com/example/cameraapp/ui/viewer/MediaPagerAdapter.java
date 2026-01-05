package com.example.cameraapp.ui.viewer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cameraapp.R;
import com.example.cameraapp.ui.gallery.MediaItem;

import java.util.ArrayList;
import java.util.List;

public class MediaPagerAdapter extends RecyclerView.Adapter<MediaPagerAdapter.MediaViewHolder> {

    private final List<MediaItem> items = new ArrayList<>();
    private OnVideoClickListener videoClickListener;
    private VideoView currentVideoView;
    private int currentVideoPosition = -1;

    public interface OnVideoClickListener {
        void onVideoClick(VideoView videoView, MediaItem item, int position);
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.videoClickListener = listener;
    }

    public void setItems(List<MediaItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public MediaItem getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void pauseCurrentVideo() {
        if (currentVideoView != null && currentVideoView.isPlaying()) {
            currentVideoView.pause();
        }
    }

    public VideoView getCurrentVideoView() {
        return currentVideoView;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_page, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem item = items.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final VideoView videoView;
        private final ProgressBar progressBar;
        private final ImageView playOverlay;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
            videoView = itemView.findViewById(R.id.video_view);
            progressBar = itemView.findViewById(R.id.progress_bar);
            playOverlay = itemView.findViewById(R.id.play_overlay);
        }

        void bind(MediaItem item, int position) {
            progressBar.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.GONE);
            playOverlay.setVisibility(View.GONE);

            if (item.isVideo()) {
                bindVideo(item, position);
            } else {
                bindImage(item);
            }
        }

        private void bindImage(MediaItem item) {
            imageView.setVisibility(View.VISIBLE);
            
            Glide.with(itemView.getContext())
                    .load(item.getUri())
                    .into(imageView);
            
            progressBar.setVisibility(View.GONE);
        }

        private void bindVideo(MediaItem item, int position) {
            videoView.setVisibility(View.VISIBLE);
            playOverlay.setVisibility(View.VISIBLE);
            
            Glide.with(itemView.getContext())
                    .load(item.getUri())
                    .into(imageView);
            imageView.setVisibility(View.VISIBLE);

            videoView.setVideoURI(item.getUri());
            
            videoView.setOnPreparedListener(mp -> {
                progressBar.setVisibility(View.GONE);
                mp.setLooping(true);
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                progressBar.setVisibility(View.GONE);
                return true;
            });

            View.OnClickListener clickListener = v -> {
                if (videoClickListener != null) {
                    currentVideoView = videoView;
                    currentVideoPosition = position;
                    
                    if (videoView.isPlaying()) {
                        videoView.pause();
                        playOverlay.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.GONE);
                    } else {
                        imageView.setVisibility(View.GONE);
                        playOverlay.setVisibility(View.GONE);
                        videoView.start();
                    }
                    videoClickListener.onVideoClick(videoView, item, position);
                }
            };

            playOverlay.setOnClickListener(clickListener);
            videoView.setOnClickListener(clickListener);
            imageView.setOnClickListener(clickListener);
        }
    }
}

