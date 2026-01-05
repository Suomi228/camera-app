package com.example.cameraapp;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.cameraapp.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupEdgeToEdge();
        setupNavigation();
    }

    private void setupEdgeToEdge() {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        
        windowInsetsController.setAppearanceLightStatusBars(true);
        windowInsetsController.setAppearanceLightNavigationBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            binding.bottomNavigation.setPadding(
                    binding.bottomNavigation.getPaddingLeft(),
                    binding.bottomNavigation.getPaddingTop(),
                    binding.bottomNavigation.getPaddingRight(),
                    insets.bottom
            );

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                
                if (navController.getCurrentDestination() != null && 
                    itemId == navController.getCurrentDestination().getId()) {
                    return true;
                }

                navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
                
                if (itemId == R.id.photoFragment) {
                    return true;
                } else if (itemId == R.id.videoFragment) {
                    navController.navigate(R.id.videoFragment);
                    return true;
                } else if (itemId == R.id.galleryFragment) {
                    navController.navigate(R.id.galleryFragment);
                    return true;
                }
                return false;
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.mediaViewerFragment) {
                    binding.bottomNavigation.setVisibility(View.GONE);
                } else {
                    binding.bottomNavigation.setVisibility(View.VISIBLE);
                    
                    int destId = destination.getId();
                    if (destId == R.id.photoFragment) {
                        binding.bottomNavigation.getMenu().findItem(R.id.photoFragment).setChecked(true);
                    } else if (destId == R.id.videoFragment) {
                        binding.bottomNavigation.getMenu().findItem(R.id.videoFragment).setChecked(true);
                    } else if (destId == R.id.galleryFragment) {
                        binding.bottomNavigation.getMenu().findItem(R.id.galleryFragment).setChecked(true);
                    }
                }
            });
        }
    }

    public NavController getNavController() {
        return navController;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
