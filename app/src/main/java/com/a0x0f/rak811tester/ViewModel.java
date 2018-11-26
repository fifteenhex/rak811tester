package com.a0x0f.rak811tester;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import com.felhr.usbserial.SerialPortBuilder;
import com.felhr.usbserial.UsbSerialDevice;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import androidx.core.app.ActivityCompat;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

public class ViewModel extends androidx.lifecycle.ViewModel implements LifecycleObserver {

    private static final String TAG = ViewModel.class.getSimpleName();

    private final FusedLocationProviderClient fusedLocationClient;
    private Rak811 rak811;
    private final PublishSubject<UsbSerialDevice> serialDriverPublishSubject = PublishSubject.create();
    private final BehaviorSubject<LocationResult> locationSubject = BehaviorSubject.create();

    private final int prefix = new Random().nextInt();
    private int serial;


    public final MapData mapData = new MapData();
    public final PublishSubject<String> messages = PublishSubject.create();
    public final ObservableList<Logger.LogLine> log = new ObservableArrayList<>();

    private Disposable serialDriverDisposable, locationDisposable;

    public ViewModel() {
        Logger.getLogLines()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(l -> {
                    log.add(0, l);
                });

        fusedLocationClient = LocationServices
                .getFusedLocationProviderClient(Rak811TesterApplication.getInstance());

        locationDisposable = locationSubject
                .observeOn(Schedulers.io())
                .toFlowable(BackpressureStrategy.LATEST)
                .toObservable()
                .flatMap(lr -> {
                    if (rak811 != null) {
                        final int serial = this.serial++;
                        ByteBuffer bb = ByteBuffer.allocate(8);
                        bb.putInt(prefix);
                        bb.putInt(serial);
                        bb.flip();
                        byte[] data = new byte[bb.limit()];
                        bb.get(data);
                        if (rak811.send(true, 1, data)) {
                            messages.onNext("new data point");
                            Rak811.Signal signal = rak811.signal();
                            return Observable.just(DataPoint.from(serial, lr, signal));
                        } else {
                            messages.onNext("failed to send message");
                            return Observable.just(DataPoint.failure(serial, lr));
                        }
                    }
                    return Observable.empty();
                })
                .subscribe(dp -> {
                    mapData.addDataPoint(dp);
                }, e -> {
                    Log.d(TAG, "", e);
                });

        serialDriverDisposable = serialDriverPublishSubject
                .observeOn(Schedulers.io())
                .flatMap(p -> {
                            Rak811 rak811 = Rak811.from(p);
                            if (rak811 != null) {
                                rak811.join();
                                return Observable.just(rak811);
                            }
                            return Observable.empty();
                        }
                )
                .subscribe(rak811 -> {
                    messages.onNext("rak811 connected and joined");
                    this.rak811 = rak811;
                }, e -> {
                    Log.d(TAG, "", e);
                });
    }

    public static class MapData extends BaseObservable {

        private long best;
        private LatLng lastKnownLocation;
        private final ArrayList<DataPoint> dataPoints = new ArrayList<>();

        @Bindable
        public Collection<MarkerOptions> getMarkers() {
            ArrayList<MarkerOptions> markers = new ArrayList<>();
            for (DataPoint dataPoint : dataPoints)
                if (dataPoint.success)
                    markers.add(dataPoint.getMarker());
            return markers;
        }

        @Bindable
        public CameraUpdate getCamera() {
            if (dataPoints.size() >= 1) {
                LatLngBounds.Builder latLngBoundsBuilder = LatLngBounds.builder();
                for (DataPoint dp : dataPoints)
                    latLngBoundsBuilder.include(new LatLng(dp.lat, dp.lon));
                return CameraUpdateFactory.newLatLngBounds(latLngBoundsBuilder.build(), 0);
            } else if (lastKnownLocation != null)
                return CameraUpdateFactory.newLatLngZoom(lastKnownLocation, 15);
            else
                return null;
        }

        public void addDataPoint(DataPoint dataPoint) {
            dataPoints.add(dataPoint);
            notifyPropertyChanged(BR.markers);
            notifyPropertyChanged(BR.camera);
            notifyPropertyChanged(BR.best);
        }

        public void updateFrom(LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());

            if (dataPoints.size() == 0)
                notifyPropertyChanged(BR.camera);
        }

        @Bindable
        public String getBest() {
            return NumberFormat.getInstance().format(best);
        }
    }

    private static class DataPoint {
        public final long timestamp;
        public final int serial;
        public final double lat;
        public final double lon;
        public final boolean success;
        public final int rssi;
        public final int snr;

        public DataPoint(int serial, double lat, double lon, boolean success, int rssi, int snr) {
            this.timestamp = System.currentTimeMillis();
            this.serial = serial;
            this.lat = lat;
            this.lon = lon;
            this.success = success;
            this.rssi = rssi;
            this.snr = snr;
        }

        public MarkerOptions getMarker() {
            return new MarkerOptions()
                    .position(new LatLng(lat, lon))
                    .title(String.format("%d", serial))
                    .snippet(String.format("%s", rssi));
        }

        public static DataPoint from(int serial, LocationResult locationResult, Rak811.Signal signal) {
            Location location = locationResult.getLastLocation();
            return new DataPoint(serial, location.getLatitude(), location.getLongitude(),
                    true, signal.rssi, signal.snr);
        }

        public static DataPoint failure(int serial, LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            return new DataPoint(serial, location.getLatitude(), location.getLongitude(),
                    false, 0, 0);
        }
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Location location = locationResult.getLastLocation();
            Logger.d(TAG, String.format("location is now: %f,%f", location.getLatitude(), location.getLongitude()));
            locationSubject.onNext(locationResult);
            mapData.updateFrom(locationResult);
        }
    };

    public LocationRequest createLocationRequest() {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(60 * 1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    @SuppressLint("MissingPermission")
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(Rak811TesterApplication.getInstance(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(createLocationRequest(), locationCallback,
                    Looper.getMainLooper());
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void findSerialPorts() {
        SerialPortBuilder.createSerialPortBuilder(ports -> {
            for (UsbSerialDevice port : ports)
                serialDriverPublishSubject.onNext(port);
        }).getSerialPorts(Rak811TesterApplication.getInstance());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        serialDriverDisposable.dispose();
        locationDisposable.dispose();
    }
}