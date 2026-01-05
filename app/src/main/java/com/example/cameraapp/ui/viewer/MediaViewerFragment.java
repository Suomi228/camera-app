package com.example.cameraapp.ui.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentMediaViewerBinding;
import com.example.cameraapp.ui.gallery.MediaItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaViewerFragment extends Fragment {

    private FragmentMediaViewerBinding binding;
    private MediaPagerAdapter adapter;
    private List<MediaItem> mediaItems;
    private int initialPosition = 0;
    private ExecutorService executor;
    private boolean isPlaying = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMediaViewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        
        parseArguments();
        setupEdgeToEdge();
        setupViewPager();
        setupControls();
    }

    private void parseArguments() {
        mediaItems = MediaCache.getInstance().getMediaItems();
        
        if (getArguments() != null) {
            initialPosition = getArguments().getInt("position", 0);
        }
    }

    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.top + v.getPaddingTop(),
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupViewPager() {
        adapter = new MediaPagerAdapter();
        adapter.setItems(mediaItems);
        
        adapter.setOnVideoClickListener((videoView, item, position) -> {
            isPlaying = videoView.isPlaying();
            updateVideoControls(item);
        });

        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setCurrentItem(initialPosition, false);
        
        updateUI(initialPosition);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.pauseCurrentVideo();
                isPlaying = false;
                updateUI(position);
            }
        });
    }

    private void updateUI(int position) {
        MediaItem item = adapter.getItem(position);
        if (item != null) {
            binding.tvTitle.setText(item.getDisplayName());
            updateVideoControls(item);
            updateCounter(position);
        }
    }

    private void updateVideoControls(MediaItem item) {
        if (item.isVideo()) {
            binding.videoControls.setVisibility(View.VISIBLE);
            binding.btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        } else {
            binding.videoControls.setVisibility(View.GONE);
        }
    }

    private void updateCounter(int position) {
        if (mediaItems.size() > 1) {
            binding.tvCounter.setVisibility(View.VISIBLE);
            binding.tvCounter.setText((position + 1) + " / " + mediaItems.size());
        } else {
            binding.tvCounter.setVisibility(View.GONE);
        }
    }

    private void setupControls() {
        binding.btnBack.setOnClickListener(v -> navigateBack());
        binding.btnDelete.setOnClickListener(v -> showDeleteDialog());
        
        binding.btnPlayPause.setOnClickListener(v -> toggleVideoPlayback());
        
        binding.viewPager.setOnClickListener(v -> toggleControlsVisibility());
    }

    private void toggleVideoPlayback() {
        VideoView videoView = adapter.getCurrentVideoView();
        if (videoView != null) {
            if (videoView.isPlaying()) {
                videoView.pause();
                isPlaying = false;
                binding.btnPlayPause.setImageResource(R.drawable.ic_play);
            } else {
                videoView.start();
                isPlaying = true;
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause);
            }
        }
    }

    private void toggleControlsVisibility() {
        if (binding.topBar.getVisibility() == View.VISIBLE) {
            binding.topBar.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> binding.topBar.setVisibility(View.GONE)).start();
            binding.videoControls.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> binding.videoControls.setVisibility(View.GONE)).start();
            binding.tvCounter.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> binding.tvCounter.setVisibility(View.GONE)).start();
        } else {
            binding.topBar.setAlpha(0f);
            binding.topBar.setVisibility(View.VISIBLE);
            binding.topBar.animate().alpha(1f).setDuration(200).start();
            
            MediaItem currentItem = adapter.getItem(binding.viewPager.getCurrentItem());
            if (currentItem != null && currentItem.isVideo()) {
                binding.videoControls.setAlpha(0f);
                binding.videoControls.setVisibility(View.VISIBLE);
                binding.videoControls.animate().alpha(1f).setDuration(200).start();
            }
            
            if (mediaItems.size() > 1) {
                binding.tvCounter.setAlpha(0f);
                binding.tvCounter.setVisibility(View.VISIBLE);
                binding.tvCounter.animate().alpha(1f).setDuration(200).start();
            }
        }
    }

    private void showDeleteDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_file)
                .setMessage("Вы уверены, что хотите удалить этот файл?")
                .setPositiveButton(R.string.delete_file, (dialog, which) -> deleteCurrentMedia())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteCurrentMedia() {
        int position = binding.viewPager.getCurrentItem();
        MediaItem item = adapter.getItem(position);
        
        if (item == null) return;

        executor.execute(() -> {
            try {
                int deleted = requireContext().getContentResolver().delete(item.getUri(), null, null);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (deleted > 0) {
                            Toast.makeText(requireContext(), "Файл удалён", Toast.LENGTH_SHORT).show();
                            
                            mediaItems.remove(position);
                            MediaCache.getInstance().setMediaItems(mediaItems);
                            
                            if (mediaItems.isEmpty()) {
                                navigateBack();
                            } else {
                                adapter.removeItem(position);
                                int newPosition = Math.min(position, mediaItems.size() - 1);
                                updateUI(newPosition);
                            }
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

    private void navigateBack() {
        Navigation.findNavController(binding.getRoot()).popBackStack();
    }

    @Override
    public void onPause() {
        super.onPause();
        adapter.pauseCurrentVideo();
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
