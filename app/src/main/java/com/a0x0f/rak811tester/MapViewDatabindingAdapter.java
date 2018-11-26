package com.a0x0f.rak811tester;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Collection;

import androidx.databinding.BindingAdapter;

public class MapViewDatabindingAdapter {

    @BindingAdapter("markers")
    public static void setMarkers(MapView mapView, final Collection<MarkerOptions> markers) {
        mapView.getMapAsync(googleMap -> {
            googleMap.clear();
            for (MarkerOptions mo : markers)
                googleMap.addMarker(mo);
        });
    }

    @BindingAdapter("camera")
    public static void setCamera(MapView mapView, final CameraUpdate cameraUpdate) {
        if (cameraUpdate == null)
            return;
        mapView.getMapAsync(googleMap -> googleMap.animateCamera(cameraUpdate));
    }

    @BindingAdapter("allGesturesEnabled")
    public static void setAllGesturesEnabled(MapView mapView, final boolean enabled) {
        mapView.getMapAsync(googleMap -> {
            googleMap.getUiSettings().setAllGesturesEnabled(enabled);
        });
    }

    @BindingAdapter("myLocationEnabled")
    public static void setMyLocationEnabled(MapView mapView, final boolean enabled) {
        mapView.getMapAsync(googleMap -> {
            try {
                googleMap.setMyLocationEnabled(enabled);
            } catch (SecurityException se) {

            }
        });
    }

    @BindingAdapter("myLocationButtonEnabled")
    public static void setMyLocationButtonEnabled(MapView mapView, final boolean enabled) {
        mapView.getMapAsync(googleMap -> {
            googleMap.getUiSettings().setMyLocationButtonEnabled(enabled);
        });
    }

    @BindingAdapter("maxZoomPreference")
    public static void setMaxZoomPreference(MapView mapView, final float zoomLevel) {
        mapView.getMapAsync(googleMap -> {
            googleMap.setMaxZoomPreference(zoomLevel);
        });
    }

}
