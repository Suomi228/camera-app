package com.example.cameraapp.ui.video;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentVideoBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class VideoFragment extends Fragment {

    private FragmentVideoBinding binding;
    private boolean isUsingFrontCamera = false;
    private boolean isRecording = false;
    private long recordingStartTime = 0;
    private Handler recordingHandler;
    private Runnable recordingRunnable;
    private ObjectAnimator recordingDotAnimator;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private Preview preview;
    
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoomRatio = 1f;

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
        setupZoomGesture();
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
    
    private void setupZoomGesture() {
        scaleGestureDetector = new ScaleGestureDetector(requireContext(), 
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    if (camera == null) return true;
                    
                    float scaleFactor = detector.getScaleFactor();
                    currentZoomRatio *= scaleFactor;
                    
                    float minZoom = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
                    float maxZoom = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
                    currentZoomRatio = Math.max(minZoom, Math.min(currentZoomRatio, maxZoom));
                    
                    camera.getCameraControl().setZoomRatio(currentZoomRatio);
                    return true;
                }
            });
    }
    
    private void setupTapToFocus() {
        binding.previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            
            if (event.getAction() == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress()) {
                focusOnPoint(event.getX(), event.getY());
            }
            return true;
        });
    }
    
    private void focusOnPoint(float x, float y) {
        if (camera == null) return;
        
        MeteringPointFactory factory = binding.previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
        
        camera.getCameraControl().startFocusAndMetering(action);
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
        startCamera();
    }

    private void showPermissionRequest() {
        binding.permissionLayout.setVisibility(View.VISIBLE);
        binding.previewView.setVisibility(View.GONE);
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(requireContext());
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(requireContext(), R.string.error_camera_init, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private void bindCameraUseCases() {
        if (cameraProvider == null || !isAdded()) return;
        
        cameraProvider.unbindAll();
        
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isUsingFrontCamera ? 
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();
        
        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());
        
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);
        
        try {
            camera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    cameraSelector,
                    preview,
                    videoCapture
            );
            
            currentZoomRatio = 1f;
            setupTapToFocus();
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.error_camera_init, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startRecording() {
        if (videoCapture == null || !hasRequiredPermissions()) return;
        
        isRecording = true;
        recordingStartTime = System.currentTimeMillis();
        
        binding.btnRecord.setBackgroundResource(R.drawable.capture_button_recording);
        binding.btnRecord.setContentDescription(getString(R.string.stop_recording));
        binding.btnSwitchCamera.setEnabled(false);
        binding.btnSwitchCamera.setAlpha(0.5f);

        binding.recordingIndicator.setVisibility(View.VISIBLE);
        startRecordingTimer();
        startRecordingDotAnimation();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        String fileName = "VID_" + timestamp + ".mp4";
        
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/CameraApp");
        }
        
        MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
                .setContentValues(contentValues)
                .build();
        
        recording = videoCapture.getOutput()
                .prepareRecording(requireContext(), outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(requireContext()), videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                        if (!finalizeEvent.hasError()) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.video_saved, Toast.LENGTH_SHORT).show();
                                if (finalizeEvent.getOutputResults().getOutputUri() != null) {
                                    com.bumptech.glide.Glide.with(requireContext())
                                            .load(finalizeEvent.getOutputResults().getOutputUri())
                                            .centerCrop()
                                            .into(binding.imgLastVideo);
                                }
                            }
                        } else {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.error_save_file, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void stopRecording() {
        if (recording == null) return;
        
        isRecording = false;
        recording.stop();
        recording = null;

        binding.btnRecord.setBackgroundResource(R.drawable.capture_button_background);
        binding.btnRecord.setContentDescription(getString(R.string.start_recording));
        binding.btnSwitchCamera.setEnabled(true);
        binding.btnSwitchCamera.setAlpha(1f);

        binding.recordingIndicator.setVisibility(View.GONE);
        stopRecordingTimer();
        stopRecordingDotAnimation();
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
        if (isRecording) return;
        
        isUsingFrontCamera = !isUsingFrontCamera;
        
        binding.btnSwitchCamera.animate()
                .rotationBy(180f)
                .setDuration(300)
                .start();

        bindCameraUseCases();
    }

    private void navigateToGallery() {
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.galleryFragment);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (hasRequiredPermissions() && cameraProvider != null && !isRecording) {
            bindCameraUseCases();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRecordingTimer();
        stopRecordingDotAnimation();
        if (recording != null) {
            recording.stop();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        binding = null;
    }
}
