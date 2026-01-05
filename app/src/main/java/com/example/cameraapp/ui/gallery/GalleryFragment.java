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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryFragment extends Fragment {

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
                openMediaViewer(item);
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
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
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
        if (hasRequiredPermissions()) {
            loadMediaFiles();
        } else {
            showEmptyState();
        }
    }

    private void loadMediaFiles() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.rvGallery.setVisibility(View.GONE);

        executor.execute(() -> {
            List<MediaItem> mediaItems = new ArrayList<>();
            mediaItems.addAll(loadImages());
            mediaItems.addAll(loadVideos());
            
            mediaItems.sort((a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));
            
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (mediaItems.isEmpty()) {
                        showEmptyState();
                    } else {
                        showGallery(mediaItems);
                    }
                });
            }
        });
    }
    
    private List<MediaItem> loadImages() {
        List<MediaItem> images = new ArrayList<>();
        ContentResolver resolver = requireContext().getContentResolver();
        
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATA
        };
        
        String selection = MediaStore.Images.Media.DATA + " LIKE ?";
        String[] selectionArgs = new String[]{"%CameraApp%"};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        
        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long dateAdded = cursor.getLong(dateColumn);
                    long size = cursor.getLong(sizeColumn);
                    
                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    
                    MediaItem item = new MediaItem.Builder()
                            .setId(id)
                            .setUri(contentUri)
                            .setType(MediaItem.MediaType.PHOTO)
                            .setDisplayName(name)
                            .setDateAdded(dateAdded)
                            .setSize(size)
                            .build();
                    
                    images.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        
        return images;
    }
    
    private List<MediaItem> loadVideos() {
        List<MediaItem> videos = new ArrayList<>();
        ContentResolver resolver = requireContext().getContentResolver();
        
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATA
        };
        
        String selection = MediaStore.Video.Media.DATA + " LIKE ?";
        String[] selectionArgs = new String[]{"%CameraApp%"};
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
        
        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long dateAdded = cursor.getLong(dateColumn);
                    long duration = cursor.getLong(durationColumn);
                    long size = cursor.getLong(sizeColumn);
                    
                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    
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
                }
            }
        } catch (Exception ignored) {
        }
        
        return videos;
    }

    private void showGallery(List<MediaItem> items) {
        binding.emptyState.setVisibility(View.GONE);
        binding.rvGallery.setVisibility(View.VISIBLE);
        adapter.setItems(items);
    }

    private void showEmptyState() {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvGallery.setVisibility(View.GONE);
    }

    private void openMediaViewer(MediaItem item) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        if (item.isPhoto()) {
            intent.setDataAndType(item.getUri(), "image/*");
        } else {
            intent.setDataAndType(item.getUri(), "video/*");
        }
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(), "Не удалось открыть файл", 
                    android.widget.Toast.LENGTH_SHORT).show();
        }
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
                            android.widget.Toast.makeText(requireContext(), "Файл удалён", 
                                    android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(requireContext(), "Не удалось удалить файл", 
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> 
                        android.widget.Toast.makeText(requireContext(), "Не удалось удалить файл", 
                                android.widget.Toast.LENGTH_SHORT).show()
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
        if (hasRequiredPermissions()) {
            loadMediaFiles();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
        binding = null;
    }
}
