package com.example.cameraapp.ui.photo;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentPhotoBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class PhotoFragment extends Fragment {

    private FragmentPhotoBinding binding;
    private boolean isUsingFrontCamera = false;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private Preview preview;
    
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoomRatio = 1f;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPhotoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupEdgeToEdge();
        setupControls();
        setupZoomGesture();
        checkPermissions();
    }

    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnFlash, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = insets.top + getResources().getDimensionPixelSize(R.dimen.spacing_sm);
            v.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupControls() {
        binding.btnCapture.setOnClickListener(v -> capturePhoto());
        binding.btnSwitchCamera.setOnClickListener(v -> switchCamera());
        binding.btnFlash.setOnClickListener(v -> toggleFlash());
        binding.lastPhotoCard.setOnClickListener(v -> navigateToGallery());
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
        showFocusIndicator(x, y);
    }
    
    private void showFocusIndicator(float x, float y) {
        binding.focusIndicator.setVisibility(View.VISIBLE);
        binding.focusIndicator.setX(x - binding.focusIndicator.getWidth() / 2f);
        binding.focusIndicator.setY(y - binding.focusIndicator.getHeight() / 2f);
        binding.focusIndicator.setAlpha(1f);
        binding.focusIndicator.setScaleX(1.5f);
        binding.focusIndicator.setScaleY(1.5f);
        
        binding.focusIndicator.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .withEndAction(() -> 
                    binding.focusIndicator.animate()
                            .alpha(0f)
                            .setStartDelay(500)
                            .setDuration(200)
                            .withEndAction(() -> binding.focusIndicator.setVisibility(View.INVISIBLE))
                            .start()
                )
                .start();
    }

    private void checkPermissions() {
        if (hasRequiredPermissions()) {
            showCameraPreview();
        } else {
            showPermissionRequest();
        }
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
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
        
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build();
        
        try {
            camera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    cameraSelector,
                    preview,
                    imageCapture
            );
            
            currentZoomRatio = 1f;
            setupTapToFocus();
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.error_camera_init, Toast.LENGTH_SHORT).show();
        }
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        
        binding.btnCapture.setEnabled(false);
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        String fileName = "IMG_" + timestamp + ".jpg";
        
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CameraApp");
        }
        
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();
        
        animateFlash();
        
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        if (!isAdded()) return;
                        binding.btnCapture.setEnabled(true);
                        
                        android.net.Uri savedUri = results.getSavedUri();
                        android.util.Log.d("PhotoFragment", "Photo saved to: " + savedUri);
                        
                        if (savedUri != null) {
                            Toast.makeText(requireContext(), 
                                "Фото сохранено: " + savedUri, Toast.LENGTH_LONG).show();
                            
                            com.bumptech.glide.Glide.with(requireContext())
                                    .load(savedUri)
                                    .centerCrop()
                                    .into(binding.imgLastPhoto);
                        } else {
                            Toast.makeText(requireContext(), 
                                "Фото сохранено (URI = null)", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (!isAdded()) return;
                        binding.btnCapture.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.error_save_file, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void animateFlash() {
        binding.flashOverlay.setAlpha(0f);
        binding.flashOverlay.setVisibility(View.VISIBLE);
        binding.flashOverlay.animate()
                .alpha(1f)
                .setDuration(50)
                .withEndAction(() -> 
                    binding.flashOverlay.animate()
                            .alpha(0f)
                            .setDuration(100)
                            .start()
                )
                .start();
    }

    private void switchCamera() {
        isUsingFrontCamera = !isUsingFrontCamera;
        
        binding.btnSwitchCamera.animate()
                .rotationBy(180f)
                .setDuration(300)
                .start();

        bindCameraUseCases();
    }

    private void toggleFlash() {
        switch (flashMode) {
            case ImageCapture.FLASH_MODE_OFF:
                flashMode = ImageCapture.FLASH_MODE_ON;
                binding.btnFlash.setImageResource(R.drawable.ic_flash_on);
                Toast.makeText(requireContext(), "Вспышка включена", Toast.LENGTH_SHORT).show();
                break;
            case ImageCapture.FLASH_MODE_ON:
                flashMode = ImageCapture.FLASH_MODE_AUTO;
                binding.btnFlash.setImageResource(R.drawable.ic_flash_auto);
                Toast.makeText(requireContext(), "Авто вспышка", Toast.LENGTH_SHORT).show();
                break;
            default:
                flashMode = ImageCapture.FLASH_MODE_OFF;
                binding.btnFlash.setImageResource(R.drawable.ic_flash_off);
                Toast.makeText(requireContext(), "Вспышка выключена", Toast.LENGTH_SHORT).show();
                break;
        }
        
        if (imageCapture != null) {
            imageCapture.setFlashMode(flashMode);
        }
    }

    private void navigateToGallery() {
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.galleryFragment);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (hasRequiredPermissions() && cameraProvider != null) {
            bindCameraUseCases();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        binding = null;
    }
}
