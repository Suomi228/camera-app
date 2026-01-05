package com.example.cameraapp.ui.gallery;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentGalleryBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryFragment extends Fragment {

    private static final String TAG = "GalleryFragment";
    private FragmentGalleryBinding binding;
    private GalleryAdapter adapter;
    private static final int GRID_SPAN_COUNT = 3;
    private ExecutorService executor;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        setupEdgeToEdge();
        setupRecyclerView();
        setupButtons();
        checkPermissionsAndLoadMedia();
    }

    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupRecyclerView() {
        adapter = new GalleryAdapter(new GalleryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MediaItem item, int position) {
                openMediaViewer(item, position);
            }

            @Override
            public void onItemLongClick(MediaItem item, int position) {
                showDeleteDialog(item, position);
            }
        });

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), GRID_SPAN_COUNT);
        binding.rvGallery.setLayoutManager(layoutManager);
        binding.rvGallery.setAdapter(adapter);

        int spacing = getResources().getDimensionPixelSize(R.dimen.gallery_item_spacing);
        binding.rvGallery.addItemDecoration(new GridSpacingItemDecoration(GRID_SPAN_COUNT, spacing));
    }

    private void setupButtons() {
        binding.btnCreateContent.setOnClickListener(v -> navigateToPhoto());
    }

    private void checkPermissionsAndLoadMedia() {
        if (hasRequiredPermissions()) {
            loadMediaFiles();
        } else {
            requestPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean images = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            boolean video = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Permission READ_MEDIA_IMAGES: " + images);
            Log.d(TAG, "Permission READ_MEDIA_VIDEO: " + video);
            return images && video;
        } else {
            boolean storage = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Permission READ_EXTERNAL_STORAGE: " + storage);
            return storage;
        }
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void handlePermissionResult(Map<String, Boolean> results) {
        Log.d(TAG, "Permission results: " + results);
        loadMediaFiles();
    }

    private void loadMediaFiles() {
        if (binding == null) return;
        
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.rvGallery.setVisibility(View.GONE);

        executor.execute(() -> {
            List<MediaItem> mediaItems = new ArrayList<>();
            
            List<MediaItem> images = loadImages();
            List<MediaItem> videos = loadVideos();
            
            Log.d(TAG, "Loaded images: " + images.size());
            Log.d(TAG, "Loaded videos: " + videos.size());
            
            mediaItems.addAll(images);
            mediaItems.addAll(videos);
            
            mediaItems.sort((a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));
            
            if (isAdded() && binding != null) {
                requireActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    
                    Log.d(TAG, "Total media items: " + mediaItems.size());
                    
                    if (mediaItems.isEmpty()) {
                        showEmptyState();
                        Toast.makeText(requireContext(), 
                            "Найдено: фото=" + images.size() + ", видео=" + videos.size(), 
                            Toast.LENGTH_LONG).show();
                    } else {
                        showGallery(mediaItems);
                    }
                });
            }
        });
    }
    
    private List<MediaItem> loadImages() {
        List<MediaItem> images = new ArrayList<>();
        
        try {
            ContentResolver resolver = requireContext().getContentResolver();
            
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            
            String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE
            };
            
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
            
            try (Cursor cursor = resolver.query(collection, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    Log.d(TAG, "Images cursor count: " + cursor.getCount());
                    
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                    int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                    int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
                    
                    int count = 0;
                    while (cursor.moveToNext() && count < 50) {
                        long id = cursor.getLong(idColumn);
                        String name = cursor.getString(nameColumn);
                        long dateAdded = cursor.getLong(dateColumn);
                        long size = cursor.getLong(sizeColumn);
                        
                        Uri contentUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        
                        Log.d(TAG, "Found image: " + name + ", uri: " + contentUri);
                        
                        MediaItem item = new MediaItem.Builder()
                                .setId(id)
                                .setUri(contentUri)
                                .setType(MediaItem.MediaType.PHOTO)
                                .setDisplayName(name)
                                .setDateAdded(dateAdded)
                                .setSize(size)
                                .build();
                        
                        images.add(item);
                        count++;
                    }
                } else {
                    Log.e(TAG, "Images cursor is null");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading images", e);
        }
        
        return images;
    }
    
    private List<MediaItem> loadVideos() {
        List<MediaItem> videos = new ArrayList<>();
        
        try {
            ContentResolver resolver = requireContext().getContentResolver();
            
            Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE
            };
            
            String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
            
            try (Cursor cursor = resolver.query(collection, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    Log.d(TAG, "Videos cursor count: " + cursor.getCount());
                    
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                    int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                    
                    int count = 0;
                    while (cursor.moveToNext() && count < 50) {
                        long id = cursor.getLong(idColumn);
                        String name = cursor.getString(nameColumn);
                        long dateAdded = cursor.getLong(dateColumn);
                        long duration = cursor.getLong(durationColumn);
                        long size = cursor.getLong(sizeColumn);
                        
                        Uri contentUri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                        
                        Log.d(TAG, "Found video: " + name + ", uri: " + contentUri);
                        
                        MediaItem item = new MediaItem.Builder()
                                .setId(id)
                                .setUri(contentUri)
                                .setType(MediaItem.MediaType.VIDEO)
                                .setDisplayName(name)
                                .setDateAdded(dateAdded)
                                .setDuration(duration)
                                .setSize(size)
                                .build();
                        
                        videos.add(item);
                        count++;
                    }
                } else {
                    Log.e(TAG, "Videos cursor is null");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading videos", e);
        }
        
        return videos;
    }

    private void showGallery(List<MediaItem> items) {
        if (binding == null) return;
        binding.emptyState.setVisibility(View.GONE);
        binding.rvGallery.setVisibility(View.VISIBLE);
        adapter.setItems(items);
    }

    private void showEmptyState() {
        if (binding == null) return;
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvGallery.setVisibility(View.GONE);
    }

    private void openMediaViewer(MediaItem item, int position) {
        com.example.cameraapp.ui.viewer.MediaCache.getInstance().setMediaItems(adapter.getItems());
        
        Bundle args = new Bundle();
        args.putInt("position", position);
        
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.action_gallery_to_viewer, args);
    }

    private void showDeleteDialog(MediaItem item, int position) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_file)
                .setMessage("Вы уверены, что хотите удалить этот файл?")
                .setPositiveButton(R.string.delete_file, (dialog, which) -> deleteItem(item, position))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteItem(MediaItem item, int position) {
        executor.execute(() -> {
            try {
                int deleted = requireContext().getContentResolver().delete(item.getUri(), null, null);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (deleted > 0) {
                            adapter.removeItem(position);
                            if (adapter.getItemCount() == 0) {
                                showEmptyState();
                            }
                            Toast.makeText(requireContext(), "Файл удалён", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Не удалось удалить файл", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> 
                        Toast.makeText(requireContext(), "Не удалось удалить файл", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void navigateToPhoto() {
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.photoFragment);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMediaFiles();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        binding = null;
    }
}
