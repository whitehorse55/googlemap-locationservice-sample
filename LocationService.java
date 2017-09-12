package com.android.njx.ifynder.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.android.njx.ifynder.Utils.API;
import com.android.njx.ifynder.Utils.CommonUtil;
import com.android.njx.ifynder.Utils.Constants;
import com.android.njx.ifynder.Utils.SharedPrefUtil;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONArrayRequestListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Created by Administrator on 5/20/2017.
 */

public class LocationService extends Service
{
    private static final String TAG = "TESTGPS";
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 1000 * 60 * 15;
    private static final float LOCATION_DISTANCE = 200f;

    LocationListener[] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.e(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate()
    {
        Log.e(TAG, "onCreate");
        initializeLocationManager();
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }

    @Override
    public void onDestroy()
    {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private class LocationListener implements android.location.LocationListener
    {
        private Location mLastLocation;
        private Location mPrevLocation;

        public LocationListener(String provider)
        {
            Log.e(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location)
        {
            Log.e(TAG, "onLocationChanged: " + location.toString());
            mPrevLocation = mLastLocation;

            mLastLocation.set(location);
            float distance = mPrevLocation.distanceTo(mLastLocation);
            addTrackedLocation(CommonUtil.formatFloat(distance));
        }

        @Override
        public void onProviderDisabled(String provider)
        {
            Log.e(TAG, "onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider)
        {
            Log.e(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            Log.e(TAG, "onStatusChanged: " + provider);
        }

        public void addTrackedLocation(String distance) {
            String userId = SharedPrefUtil.loadString(LocationService.this, Constants.key_defaults_userId, "");
            String signature = SharedPrefUtil.loadString(LocationService.this, Constants.key_defaults_signature, "");
            String paramUrl = userId + "/" + signature + "/" + "1";

            String distanceDescription = "Distance from last location : %@ meter";
            JSONObject param = new JSONObject();
            try {
                param.put("userid", userId);
                param.put("signature", signature);
                param.put("lattitude", mLastLocation.getLatitude());
                param.put("longitude", mLastLocation.getLongitude());
                param.put("description", distanceDescription.replace("%@", distance));
                param.put("trackeddatetime", CommonUtil.getGMTStringWithDate(new Date(), null));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Log.e(TAG, paramUrl);
            AndroidNetworking.post(API.ADD_LOCATION_TRACK + paramUrl)
                    .addJSONArrayBody(new JSONArray().put(param))
                    .setTag("LocationList")
                    .setPriority(Priority.MEDIUM)
                    .build()
                    .getAsJSONArray(new JSONArrayRequestListener() {
                        @Override
                        public void onResponse(JSONArray response) {
                            Log.e("Track response", response.toString());
                            try {
                                for (int i = 0; i < response.length(); i++) {
                                    JSONObject temp = (JSONObject) response.get(i);
                                    String userId = temp.getString("id");
                                    String clientId = temp.getString("clientid");
                                    String dateTime = temp.getString("datetime");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError(ANError anError) {
                            Log.e("Track error", anError.getMessage());
                        }
                    });
        }
    }
}