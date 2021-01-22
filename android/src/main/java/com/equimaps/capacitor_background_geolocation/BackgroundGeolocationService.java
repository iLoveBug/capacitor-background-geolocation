package com.equimaps.capacitor_background_geolocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.getcapacitor.Logger;


import java.util.HashSet;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// A bound and started service that is promoted to a foreground service when
// location updates have been requested and the main activity is stopped.
//
// When an activity is bound to this service, frequent location updates are
// permitted. When the activity is removed from the foreground, the service
// promotes itself to a foreground service, and location updates continue. When
// the activity comes back to the foreground, the foreground service stops, and
// the notification associated with that service is removed.
public class BackgroundGeolocationService extends Service {
    static final String ACTION_BROADCAST = (
            BackgroundGeolocationService.class.getPackage().getName() + ".broadcast"
    );
    private final IBinder binder = new LocalBinder();

    // Must be unique for this application.
    private static final int NOTIFICATION_ID = 28351;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private class Watcher {
        public String id;
        public LocationListener listener;
        public Notification backgroundNotification;
    }

    private HashSet<Watcher> watchers = new HashSet<Watcher>();

    Notification getNotification() {
        for (Watcher watcher : watchers) {
            if (watcher.backgroundNotification != null) {
                return watcher.backgroundNotification;
            }
        }
        return null;
    }

    // Handles requests from the activity.
    public class LocalBinder extends Binder {
        @SuppressLint("MissingPermission")
        void addWatcher(
                final String id,
                Notification backgroundNotification
        ) {
            LocationListener ll = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Logger.debug("Location changed: " + location);
                    Intent intent = new Intent(ACTION_BROADCAST);
                    intent.putExtra("location", location);
                    intent.putExtra("id", id);
                    LocalBroadcastManager.getInstance(
                            getApplicationContext()
                    ).sendBroadcast(intent);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    Logger.debug("Location Status changed to: " + status);
                }

                @Override
                public void onProviderEnabled(String provider) {
                    Logger.debug("LocationProvider enabled");
                }

                @Override
                public void onProviderDisabled(String provider) {
                    Logger.debug("LocationProvider disabled");
                }
            };
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


            Watcher watcher = new Watcher();
            watcher.id = id;
            watcher.listener = ll;
            watcher.backgroundNotification = backgroundNotification;
            watchers.add(watcher);

            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, (float) 10.0, ll);
        }

        void removeWatcher(String id) {
            for (Watcher watcher : watchers) {
                if (watcher.id.equals(id)) {
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    lm.removeUpdates(watcher.listener);
                    watchers.remove(watcher);
                    if (getNotification() == null) {
                        stopForeground(true);
                    }
                    return;
                }
            }
        }

        @SuppressLint("MissingPermission")
        void onPermissionsGranted() {
            // If permissions were granted while the app was in the background, for example in
            // the Settings app, the watchers need restarting.
            for (Watcher watcher : watchers) {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                lm.removeUpdates(watcher.listener);
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, (float) 10.0, watcher.listener);
            }
        }

        void onActivityStarted() {
            stopForeground(true);
        }

        void onActivityStopped() {
            Notification notification = getNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
            }
        }

        void stopService() {
            BackgroundGeolocationService.this.stopSelf();
        }
    }
}
