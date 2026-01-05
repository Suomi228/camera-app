package com.example.cameraapp.ui.gallery;

import android.net.Uri;

import java.util.Date;

public class MediaItem {

    public enum MediaType {
        PHOTO,
        VIDEO
    }

    private final long id;
    private final Uri uri;
    private final MediaType type;
    private final String displayName;
    private final long dateAdded;
    private final long duration;
    private final long size;

    private MediaItem(Builder builder) {
        this.id = builder.id;
        this.uri = builder.uri;
        this.type = builder.type;
        this.displayName = builder.displayName;
        this.dateAdded = builder.dateAdded;
        this.duration = builder.duration;
        this.size = builder.size;
    }

    public long getId() {
        return id;
    }

    public Uri getUri() {
        return uri;
    }

    public MediaType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public Date getDate() {
        return new Date(dateAdded * 1000);
    }

    public long getDuration() {
        return duration;
    }

    public long getSize() {
        return size;
    }

    public boolean isPhoto() {
        return type == MediaType.PHOTO;
    }

    public boolean isVideo() {
        return type == MediaType.VIDEO;
    }

    public String getFormattedDuration() {
        if (duration <= 0) return "";

        long seconds = (duration / 1000) % 60;
        long minutes = (duration / (1000 * 60)) % 60;
        long hours = duration / (1000 * 60 * 60);

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public static class Builder {
        private long id;
        private Uri uri;
        private MediaType type;
        private String displayName;
        private long dateAdded;
        private long duration;
        private long size;

        public Builder setId(long id) {
            this.id = id;
            return this;
        }

        public Builder setUri(Uri uri) {
            this.uri = uri;
            return this;
        }

        public Builder setType(MediaType type) {
            this.type = type;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder setDateAdded(long dateAdded) {
            this.dateAdded = dateAdded;
            return this;
        }

        public Builder setDuration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder setSize(long size) {
            this.size = size;
            return this;
        }

        public MediaItem build() {
            return new MediaItem(this);
        }
    }
}
