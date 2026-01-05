package com.example.cameraapp.ui.viewer;

import com.example.cameraapp.ui.gallery.MediaItem;

import java.util.ArrayList;
import java.util.List;

public class MediaCache {
    
    private static MediaCache instance;
    private List<MediaItem> mediaItems = new ArrayList<>();
    
    private MediaCache() {}
    
    public static synchronized MediaCache getInstance() {
        if (instance == null) {
            instance = new MediaCache();
        }
        return instance;
    }
    
    public void setMediaItems(List<MediaItem> items) {
        mediaItems.clear();
        mediaItems.addAll(items);
    }
    
    public List<MediaItem> getMediaItems() {
        return new ArrayList<>(mediaItems);
    }
    
    public void clear() {
        mediaItems.clear();
    }
}

