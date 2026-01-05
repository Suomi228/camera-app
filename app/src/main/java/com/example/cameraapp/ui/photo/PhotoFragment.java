package com.example.cameraapp.ui.photo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import com.example.cameraapp.R;
import com.example.cameraapp.databinding.FragmentPhotoBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PhotoFragment extends Fragment {

    private FragmentPhotoBinding binding;
    private boolean isUsingFrontCamera = false;
    private int flashMode = 0;

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
        checkPermissions();
    }

    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topControls, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.top + getResources().getDimensionPixelSize(R.dimen.spacing_sm),
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
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
    }

    private void showPermissionRequest() {
        binding.permissionLayout.setVisibility(View.VISIBLE);
        binding.previewView.setVisibility(View.GONE);
    }

    private void capturePhoto() {
        animateFlash();
        Toast.makeText(requireContext(), R.string.photo_saved, Toast.LENGTH_SHORT).show();
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

        String cameraName = isUsingFrontCamera ? "фронтальную" : "основную";
        Toast.makeText(requireContext(), "Переключено на " + cameraName + " камеру", 
                Toast.LENGTH_SHORT).show();
    }

    private void toggleFlash() {
        flashMode = (flashMode + 1) % 3;
        
        int iconRes;
        String modeName;
        switch (flashMode) {
            case 1:
                iconRes = R.drawable.ic_flash_on;
                modeName = "Вспышка включена";
                break;
            case 2:
                iconRes = R.drawable.ic_flash_auto;
                modeName = "Авто вспышка";
                break;
            default:
                iconRes = R.drawable.ic_flash_off;
                modeName = "Вспышка выключена";
                break;
        }
        
        binding.btnFlash.setImageResource(iconRes);
        Toast.makeText(requireContext(), modeName, Toast.LENGTH_SHORT).show();
    }

    private void navigateToGallery() {
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.galleryFragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
