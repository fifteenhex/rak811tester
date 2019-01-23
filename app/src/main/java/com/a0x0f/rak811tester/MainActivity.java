package com.a0x0f.rak811tester;


import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.a0x0f.rak811tester.databinding.ActivityMainBinding;
import com.a0x0f.rak811tester.databinding.DialogRak811settingsBinding;
import com.a0x0f.rak811tester.databinding.DialogStatusBinding;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.Task;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private final int REQ_LOCATION_PERMISSION = 1;
    private final int REQ_LOCATIONRESOLUTION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ViewModel viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        getLifecycle().addObserver(viewModel);

        viewModel.messages
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(msg -> {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setViewModel(viewModel);
        binding.map.onCreate(savedInstanceState);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION_PERMISSION);
        }


        LocationSettingsRequest.Builder lsrBuilder = new LocationSettingsRequest.Builder()
                .addLocationRequest(getViewModel().createLocationRequest());
        Task<LocationSettingsResponse> result =
                LocationServices.getSettingsClient(this).checkLocationSettings(lsrBuilder.build());
        result.addOnCompleteListener(task -> {
            try {
                LocationSettingsResponse response = task.getResult(ApiException.class);
            } catch (ApiException exception) {
                switch (exception.getStatusCode()) {
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) exception;
                            resolvable.startResolutionForResult(this,
                                    REQ_LOCATIONRESOLUTION);
                        } catch (IntentSender.SendIntentException e) {
                        } catch (ClassCastException e) {
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        break;
                }
            }
        });

        binding.map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.map.onPause();
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_LOCATION_PERMISSION)
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ViewModelProviders.of(this).get(ViewModel.class)
                        .startLocationUpdates();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    protected ViewModel getViewModel() {
        return ViewModelProviders.of(this).get(ViewModel.class);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    private void showRak811Settings() {
        DialogRak811settingsBinding binding = DataBindingUtil.inflate(getLayoutInflater(),
                R.layout.dialog_rak811settings, null, false);
        binding.setViewModel(getViewModel());

        new AlertDialog.Builder(this)
                .setTitle("Rak811 Settings")
                .setView(binding.getRoot())
                .setNegativeButton("Fuggedaboutit!", (dialog, which) -> {
                        }
                )
                .setPositiveButton("Okie dokie", (dialog, which) -> {
                        }
                )
                .show();
    }

    private void showStatus() {
        DialogStatusBinding binding = DataBindingUtil.inflate(getLayoutInflater(),
                R.layout.dialog_status, null, false);
        binding.setViewModel(getViewModel());

        new AlertDialog.Builder(this)
                .setTitle("Status")
                .setView(binding.getRoot())
                .setPositiveButton("Okie dokie", (dialog, which) -> {
                        }
                )
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_rak811_settings:
                showRak811Settings();
                return true;
            case R.id.action_status:
                showStatus();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
