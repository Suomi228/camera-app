package com.example.cameraapp.ui.video;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
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

import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentVideoBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VideoFragment extends Fragment {

    private FragmentVideoBinding binding;
    private boolean isUsingFrontCamera = false;
    private boolean isRecording = false;
    private long recordingStartTime = 0;
    private Handler recordingHandler;
    private Runnable recordingRunnable;
    private ObjectAnimator recordingDotAnimator;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recordingHandler = new Handler(Looper.getMainLooper());
        setupEdgeToEdge();
        setupControls();
        checkPermissions();
    }

    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.recordingIndicator, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = insets.top + getResources().getDimensionPixelSize(R.dimen.spacing_lg);
            v.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupControls() {
        binding.btnRecord.setOnClickListener(v -> toggleRecording());
        binding.btnSwitchCamera.setOnClickListener(v -> switchCamera());
        binding.lastVideoCard.setOnClickListener(v -> navigateToGallery());
        binding.btnGrantPermission.setOnClickListener(v -> requestPermissions());
    }

    private void checkPermissions() {
        if (hasRequiredPermissions()) {
            showCameraPreview();
        } else {
            showPermissionRequest();
        }
    }

    private boolean hasRequiredPermissions() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean audioGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        return cameraGranted && audioGranted;
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void handlePermissionResult(Map<String, Boolean> results) {
        boolean allGranted = true;
        for (Boolean granted : results.values()) {
            if (!granted) {
                allGranted = false;
                break;
            }
        }

        if (allGranted || hasRequiredPermissions()) {
            showCameraPreview();
        } else {
            Toast.makeText(requireContext(), R.string.camera_permission_rationale,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showCameraPreview() {
        binding.permissionLayout.setVisibility(View.GONE);
        binding.previewView.setVisibility(View.VISIBLE);
    }

    private void showPermissionRequest() {
        binding.permissionLayout.setVisibility(View.VISIBLE);
        binding.previewView.setVisibility(View.GONE);
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        isRecording = true;
        recordingStartTime = System.currentTimeMillis();

        binding.btnRecord.setBackgroundResource(R.drawable.capture_button_recording);
        binding.btnRecord.setContentDescription(getString(R.string.stop_recording));

        binding.recordingIndicator.setVisibility(View.VISIBLE);
        startRecordingTimer();
        startRecordingDotAnimation();

        Toast.makeText(requireContext(), "Запись начата", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        isRecording = false;

        binding.btnRecord.setBackgroundResource(R.drawable.capture_button_background);
        binding.btnRecord.setContentDescription(getString(R.string.start_recording));

        binding.recordingIndicator.setVisibility(View.GONE);
        stopRecordingTimer();
        stopRecordingDotAnimation();

        Toast.makeText(requireContext(), R.string.video_saved, Toast.LENGTH_SHORT).show();
    }

    private void startRecordingTimer() {
        recordingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording && binding != null) {
                    long elapsedTime = System.currentTimeMillis() - recordingStartTime;
                    binding.tvRecordingTime.setText(formatDuration(elapsedTime));
                    recordingHandler.postDelayed(this, 1000);
                }
            }
        };
        recordingHandler.post(recordingRunnable);
    }

    private void stopRecordingTimer() {
        if (recordingRunnable != null) {
            recordingHandler.removeCallbacks(recordingRunnable);
        }
    }

    private void startRecordingDotAnimation() {
        recordingDotAnimator = ObjectAnimator.ofFloat(binding.recordingDot, "alpha", 1f, 0.3f);
        recordingDotAnimator.setDuration(500);
        recordingDotAnimator.setRepeatMode(ValueAnimator.REVERSE);
        recordingDotAnimator.setRepeatCount(ValueAnimator.INFINITE);
        recordingDotAnimator.setInterpolator(new LinearInterpolator());
        recordingDotAnimator.start();
    }

    private void stopRecordingDotAnimation() {
        if (recordingDotAnimator != null) {
            recordingDotAnimator.cancel();
            binding.recordingDot.setAlpha(1f);
        }
    }

    private String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = millis / (1000 * 60 * 60);

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void switchCamera() {
        isUsingFrontCamera = !isUsingFrontCamera;
        
        binding.btnSwitchCamera.animate()
                .rotationBy(180f)
                .setDuration(300)
                .start();

        String cameraName = isUsingFrontCamera ? "фронтальную" : "основную";
        Toast.makeText(requireContext(), "Переключено на " + cameraName + " камеру",
                Toast.LENGTH_SHORT).show();
    }

    private void navigateToGallery() {
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.galleryFragment);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRecordingTimer();
        stopRecordingDotAnimation();
        binding = null;
    }
}
