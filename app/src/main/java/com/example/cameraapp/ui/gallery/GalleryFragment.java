package com.example.cameraapp.ui.gallery;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;
    private GalleryAdapter adapter;
    private static final int GRID_SPAN_COUNT = 3;

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

        binding.progressBar.setVisibility(View.GONE);
        
        List<MediaItem> mediaItems = new ArrayList<>();
        
        if (mediaItems.isEmpty()) {
            showEmptyState();
        } else {
            showGallery(mediaItems);
        }
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
        adapter.removeItem(position);
        
        if (adapter.getItemCount() == 0) {
            showEmptyState();
        }
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
        binding = null;
    }
}
