package com.example.cameraapp.ui.viewer;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentMediaViewerBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaViewerFragment extends Fragment {

    private FragmentMediaViewerBinding binding;
    private Uri mediaUri;
    private boolean isVideo;
    private String displayName;
    private ExecutorService executor;
    
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1f;
    private float translateX = 0f;
    private float translateY = 0f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean isScaling = false;

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
        setupControls();
        setupZoomGesture();
        loadMedia();
    }

    private void parseArguments() {
        if (getArguments() != null) {
            String uriString = getArguments().getString("uri");
            if (uriString != null) {
                mediaUri = Uri.parse(uriString);
            }
            isVideo = getArguments().getBoolean("isVideo", false);
            displayName = getArguments().getString("displayName", "");
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

    private void setupControls() {
        binding.tvTitle.setText(displayName);
        
        binding.btnBack.setOnClickListener(v -> navigateBack());
        binding.btnDelete.setOnClickListener(v -> showDeleteDialog());
        
        binding.btnPlayPause.setOnClickListener(v -> toggleVideoPlayback());
        
        binding.viewerContainer.setOnClickListener(v -> toggleControlsVisibility());
    }
    
    private void setupZoomGesture() {
        scaleGestureDetector = new ScaleGestureDetector(requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        isScaling = true;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        scaleFactor *= detector.getScaleFactor();
                        scaleFactor = Math.max(1f, Math.min(scaleFactor, 5f));
                        applyTransform();
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        isScaling = false;
                    }
                });

        binding.imageView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!isScaling && scaleFactor > 1f) {
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        translateX += dx;
                        translateY += dy;
                        applyTransform();
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (!isScaling && scaleFactor == 1f) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }

    private void applyTransform() {
        binding.imageView.setScaleX(scaleFactor);
        binding.imageView.setScaleY(scaleFactor);
        binding.imageView.setTranslationX(translateX);
        binding.imageView.setTranslationY(translateY);
    }

    private void resetTransform() {
        scaleFactor = 1f;
        translateX = 0f;
        translateY = 0f;
        applyTransform();
    }

    private void loadMedia() {
        if (mediaUri == null) {
            Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
            navigateBack();
            return;
        }

        if (isVideo) {
            loadVideo();
        } else {
            loadImage();
        }
    }

    private void loadImage() {
        binding.imageView.setVisibility(View.VISIBLE);
        binding.videoView.setVisibility(View.GONE);
        binding.videoControls.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(mediaUri)
                .into(binding.imageView);
        
        binding.progressBar.setVisibility(View.GONE);
    }

    private void loadVideo() {
        binding.imageView.setVisibility(View.GONE);
        binding.videoView.setVisibility(View.VISIBLE);
        binding.videoControls.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);

        binding.videoView.setVideoURI(mediaUri);
        
        binding.videoView.setOnPreparedListener(mp -> {
            binding.progressBar.setVisibility(View.GONE);
            mp.setLooping(true);
        });

        binding.videoView.setOnErrorListener((mp, what, extra) -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void toggleVideoPlayback() {
        if (binding.videoView.isPlaying()) {
            binding.videoView.pause();
            binding.btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            binding.videoView.start();
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause);
        }
    }

    private void toggleControlsVisibility() {
        if (binding.topBar.getVisibility() == View.VISIBLE) {
            binding.topBar.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> binding.topBar.setVisibility(View.GONE)).start();
            if (isVideo) {
                binding.videoControls.animate().alpha(0f).setDuration(200)
                        .withEndAction(() -> binding.videoControls.setVisibility(View.GONE)).start();
            }
        } else {
            binding.topBar.setAlpha(0f);
            binding.topBar.setVisibility(View.VISIBLE);
            binding.topBar.animate().alpha(1f).setDuration(200).start();
            if (isVideo) {
                binding.videoControls.setAlpha(0f);
                binding.videoControls.setVisibility(View.VISIBLE);
                binding.videoControls.animate().alpha(1f).setDuration(200).start();
            }
        }
    }

    private void showDeleteDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_file)
                .setMessage("Вы уверены, что хотите удалить этот файл?")
                .setPositiveButton(R.string.delete_file, (dialog, which) -> deleteMedia())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteMedia() {
        executor.execute(() -> {
            try {
                int deleted = requireContext().getContentResolver().delete(mediaUri, null, null);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (deleted > 0) {
                            Toast.makeText(requireContext(), "Файл удалён", Toast.LENGTH_SHORT).show();
                            navigateBack();
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
        if (binding.videoView.isPlaying()) {
            binding.videoView.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        binding.videoView.stopPlayback();
        binding = null;
    }
}

