package com.a0x0f.rak811tester;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Tasks;

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
import io.reactivex.Observer;
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
                                rak811Config.update(rak811);
                                rak811.getBand();
                                if (rak811.join()) {
                                    status.update(rak811);
                                    return Observable.just(rak811);
                                } else
                                    messages.onNext("join failed");
                            }
                            return Observable.empty();
                        }
                )
                .subscribe(rak811 -> {
                    messages.onNext("rak811 connected and joined");
                    Rak811.Signal signal = rak811.signal();
                    try {
                        Location location =
                                Tasks.await(fusedLocationClient.getLastLocation());
                        DataPoint dp = new DataPoint(-1, location,
                                true, signal.rssi, signal.snr);
                        dp.origin = true;
                        mapData.setOrigin(dp);
                        messages.onNext("origin set");
                    } catch (SecurityException se) {

                    }
                    this.rak811 = rak811;
                }, e -> {
                    Log.d(TAG, "", e);
                });
    }

    public static class SnrRssi extends BaseObservable {
        public float rssi;
        public float snr;

        @Bindable
        public String getRssi() {
            return NumberFormat.getInstance().format(rssi);
        }

        @Bindable
        public String getSnr() {
            return NumberFormat.getInstance().format(snr);
        }

        private void updateRssiSnr(DataPoint dataPoint) {
            rssi = dataPoint.rssi;
            snr = dataPoint.snr;
            notifyPropertyChanged(BR.rssi);
            notifyPropertyChanged(BR.snr);
        }

        private void updateRssiSnrIfWorse(DataPoint dataPoint) {
            if (dataPoint.rssi < rssi)
                updateRssiSnr(dataPoint);
        }

        private void updateRssiSnrIfBetter(DataPoint dataPoint) {
            if (dataPoint.rssi > rssi)
                updateRssiSnr(dataPoint);
        }
    }

    public static class MapData extends BaseObservable {

        private float best;

        public final SnrRssi bestSignal = new SnrRssi();
        public final SnrRssi currentSignal = new SnrRssi();
        public final SnrRssi worstSignal = new SnrRssi();

        private LatLng lastKnownLocation;
        private DataPoint origin;
        private final ArrayList<DataPoint> dataPoints = new ArrayList<>();

        @Bindable
        public Collection<MarkerOptions> getMarkers() {
            ArrayList<MarkerOptions> markers = new ArrayList<>();
            if (origin != null)
                markers.add(origin.getMarker());
            for (DataPoint dataPoint : dataPoints)
                if (dataPoint.success)
                    markers.add(dataPoint.getMarker());
            return markers;
        }

        @Bindable
        public CameraUpdate getCamera() {
            if (origin != null || dataPoints.size() >= 1) {
                LatLngBounds.Builder latLngBoundsBuilder = LatLngBounds.builder();
                if (origin != null)
                    latLngBoundsBuilder.include(new LatLng(origin.location.getLatitude(),
                            origin.location.getLongitude()));
                for (DataPoint dp : dataPoints)
                    latLngBoundsBuilder.include(new LatLng(dp.location.getLatitude(),
                            dp.location.getLongitude()));
                return CameraUpdateFactory.newLatLngBounds(latLngBoundsBuilder.build(), 0);
            } else if (lastKnownLocation != null)
                return CameraUpdateFactory.newLatLngZoom(lastKnownLocation, 15);
            else
                return null;
        }

        public void updateSignals(DataPoint dataPoint) {
            currentSignal.updateRssiSnr(dataPoint);
            bestSignal.updateRssiSnrIfBetter(dataPoint);
            worstSignal.updateRssiSnrIfWorse(dataPoint);
        }

        public void setOrigin(DataPoint origin) {
            this.origin = origin;
            notifyPropertyChanged(BR.markers);
            notifyPropertyChanged(BR.camera);
            currentSignal.updateRssiSnr(origin);
            bestSignal.updateRssiSnr(origin);
            worstSignal.updateRssiSnr(origin);
        }


        public void addDataPoint(DataPoint dataPoint) {
            dataPoints.add(dataPoint);
            if (origin != null) {
                float distance = dataPoint.location.distanceTo(origin.location);
                best = Math.max(best, distance);
            }

            notifyPropertyChanged(BR.markers);
            notifyPropertyChanged(BR.camera);
            notifyPropertyChanged(BR.best);
            updateSignals(dataPoint);
        }

        public void updateFrom(LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
            if (dataPoints.size() == 0)
                notifyPropertyChanged(BR.camera);
        }

        @Bindable
        public String getBest() {
            return String.format("%s meters", NumberFormat.getInstance().format(best));
        }


    }

    private static class DataPoint {

        private static final Bitmap originMarker;
        private static final Bitmap dataPointMarkerStrong;
        private static final Bitmap dataPointMarkerNormal;
        private static final Bitmap dataPointMarkerWeak;

        private static Bitmap makeDot(int fillColour) {
            int size = 32;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

            Canvas c = new Canvas(bitmap);
            c.translate(size / 2, size / 2);

            Paint fill = new Paint();
            fill.setColor(fillColour);
            fill.setAntiAlias(true);
            c.drawCircle(0, 0, size / 2, fill);

            Paint ring = new Paint();
            ring.setARGB(0xff, 0xff, 0xff, 0xff);
            ring.setAntiAlias(true);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2);
            c.drawCircle(0, 0, size / 2, ring);
            return bitmap;
        }

        static {
            originMarker = makeDot(0xffb61cd1);
            dataPointMarkerStrong = makeDot(0xff1cd12f);
            dataPointMarkerNormal = makeDot(0xffd1c41c);
            dataPointMarkerWeak = makeDot(0xffd11c1c);
        }

        public final long timestamp;
        public final int serial;
        public final Location location;
        public final boolean success;
        public final int rssi;
        public final int snr;

        public boolean origin;

        public DataPoint(int serial, Location location, boolean success, int rssi, int snr) {
            this.timestamp = System.currentTimeMillis();
            this.serial = serial;
            this.location = location;
            this.success = success;
            this.rssi = rssi;
            this.snr = snr;
        }

        public MarkerOptions getMarker() {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(new LatLng(location.getLatitude(), location.getLongitude()))
                    .title(String.format("%d", serial))
                    .snippet(String.format("%s", rssi));
            Bitmap marker;
            if (origin)
                marker = originMarker;
            else {
                if (rssi <= -100)
                    marker = dataPointMarkerWeak;
                else if (rssi <= 70)
                    marker = dataPointMarkerNormal;
                else
                    marker = dataPointMarkerNormal;
            }

            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(marker));

            return markerOptions;
        }

        public static DataPoint from(int serial, LocationResult locationResult, Rak811.Signal signal) {
            Location location = locationResult.getLastLocation();
            return new DataPoint(serial, location,
                    true, signal.rssi, signal.snr);
        }

        public static DataPoint failure(int serial, LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            return new DataPoint(serial, location,
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
                .setSmallestDisplacement(25)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    @SuppressLint("MissingPermission")
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(Rak811TesterApplication.getInstance(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(createLocationRequest(),
                    locationCallback,
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

    public static class Rak811Config extends BaseObservable {
        @Bindable
        public String appEui;
        @Bindable
        public String devEui;


        public void update(Rak811 rak811) {
            appEui = rak811.getAppEui();
            devEui = rak811.getDevEui();

            notifyPropertyChanged(BR.appEui);
            notifyPropertyChanged(BR.devEui);
        }
    }

    public final Rak811Config rak811Config = new Rak811Config();

    public static class Status extends BaseObservable {
        public String firmware;
        public String devAddr;

        public void update(Rak811 rak811) {
            rak811.getVersion();
            rak811.getDevAddr();
        }
    }

    public final Status status = new Status();
}