/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.anwar1909.bgloc.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.media.AudioManager;
import android.provider.Settings;
import android.widget.Toast;
import android.os.Build;
import com.google.android.gms.location.DetectedActivity;
import com.anwar1909.bgloc.Config;
import com.anwar1909.bgloc.PluginException;
import com.anwar1909.bgloc.data.BackgroundActivity;
import com.anwar1909.bgloc.data.BackgroundLocation;
import com.anwar1909.bgloc.data.BatteryUtils;
import com.anwar1909.logging.LoggerManager;
import com.anwar1909.utils.ToneGenerator;
import com.anwar1909.utils.ToneGenerator.Tone;
import com.anwar1909x.ActivityRecognition.ActivityTransitionService;

import java.lang.reflect.Field;

/**
 * AbstractLocationProvider
 */
public abstract class AbstractLocationProvider implements LocationProvider {

    protected Integer PROVIDER_ID;
    protected Config mConfig;
    protected Context mContext;

    protected ToneGenerator toneGenerator;
    protected org.slf4j.Logger logger;

    private ProviderDelegate mDelegate;

    private ActivityTransitionService mActivityTransitionDetectionService;
    private Location lastLocation;

    protected AbstractLocationProvider(Context context, Integer provider_id) {
        mContext = context;
        logger = LoggerManager.getLogger(getClass());
        this.PROVIDER_ID=provider_id;
        if (provider_id == Config.FUSED_PROVIDER) {
            mActivityTransitionDetectionService = new ActivityTransitionService(mContext);
        }
        logger.info("Creating {}", getClass().getSimpleName());
    }

    @Override
    public void onCreate() {
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        if (mActivityTransitionDetectionService != null){
            mActivityTransitionDetectionService.onCreate();
        }
    }

    @Override
    public void onStart() {
        if (mActivityTransitionDetectionService != null){
            mActivityTransitionDetectionService.startActivity();
        }
    }

    @Override
    public void onStop() {
        if (mActivityTransitionDetectionService != null) {
            mActivityTransitionDetectionService.stopActivity();
        }
    }

    @Override
    public void onDestroy() {
        toneGenerator.release();
        if (mActivityTransitionDetectionService != null) {
            mActivityTransitionDetectionService.onDestroy();
        }
        toneGenerator = null;
    }

    @Override
    public void onConfigure(Config config) {
        mConfig = config;
        if (mActivityTransitionDetectionService != null) {
            mActivityTransitionDetectionService.setProperties(this.mConfig, this.PROVIDER_ID);
        }
    }

    @Override
    public void onCommand(int commandId, int arg1) {
        // override in child class
    }

    public void setDelegate(ProviderDelegate delegate) {
        mDelegate = delegate;
        if (mActivityTransitionDetectionService != null) {
            mActivityTransitionDetectionService.setDelegate(delegate);
        }
    }

    /**
     * Register broadcast reciever
     * @param receiver
     */
    protected Intent registerReceiver (BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Field receiverExportedField = Context.class.getField("RECEIVER_NOT_EXPORTED");
                int receiverExported = receiverExportedField.getInt(null);
                return mContext.registerReceiver(receiver, filter, receiverExported);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

        }
        return mContext.registerReceiver(receiver, filter);
    }

    /**
     * Unregister broadcast reciever
     * @param receiver
     */
    protected void unregisterReceiver (BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
    }

    /**
     * Handle location as recorder by provider
     * @param location
     */
    protected void handleLocation (Location location) {
        playDebugTone(Tone.BEEP);
        if (mDelegate != null) {
            //Prevent Duplicate Location
            if(lastLocation != null) {
                if(lastLocation.getTime() == location.getTime()) return;
            }
            lastLocation = location;

            BatteryUtils.BatteryInfo batteryInfo = BatteryUtils.getBatteryStatus(mContext);
            BackgroundLocation bgLocation = BackgroundLocation.fromLocation(location);
            bgLocation.setLocationProvider(PROVIDER_ID);
            bgLocation.setBatteryLevel(batteryInfo.getBatteryPercentage());
            bgLocation.setIsCharging(batteryInfo.isCharging());
            bgLocation.setMockLocationsEnabled(hasMockLocationsEnabled());
            mDelegate.onLocation(bgLocation);
        }
    }

    /**
     * Handle stationary location with radius
     *
     * @param location
     * @param radius radius of stationary region
     */
    protected void handleStationary (Location location, float radius) {
        playDebugTone(Tone.LONG_BEEP);
        if (mDelegate != null) {
            BatteryUtils.BatteryInfo batteryInfo = BatteryUtils.getBatteryStatus(mContext);
            BackgroundLocation bgLocation = BackgroundLocation.fromLocation(location);
            bgLocation.setLocationProvider(PROVIDER_ID);
            bgLocation.setBatteryLevel(batteryInfo.getBatteryPercentage());
            bgLocation.setIsCharging(batteryInfo.isCharging());
            bgLocation.setMockLocationsEnabled(hasMockLocationsEnabled());
            bgLocation.setRadius(radius);
            mDelegate.onStationary(bgLocation);
        }
    }

    /**
     * Handle stationary location without radius
     *
     * @param location
     */
    protected void handleStationary (Location location) {
        playDebugTone(Tone.LONG_BEEP);
        if (mDelegate != null) {
            BatteryUtils.BatteryInfo batteryInfo = BatteryUtils.getBatteryStatus(mContext);
            BackgroundLocation bgLocation = BackgroundLocation.fromLocation(location);
            bgLocation.setLocationProvider(PROVIDER_ID);
            bgLocation.setBatteryLevel(batteryInfo.getBatteryPercentage());
            bgLocation.setIsCharging(batteryInfo.isCharging());
            bgLocation.setMockLocationsEnabled(hasMockLocationsEnabled());
            mDelegate.onStationary(bgLocation);
        }
    }

    protected void handleActivity(DetectedActivity activity) {
        if (mDelegate != null) {
            mDelegate.onActivity(new BackgroundActivity(PROVIDER_ID, activity));
        }
    }

    /**
     * Handle security exception
     * @param exception
     */
    protected void handleSecurityException (SecurityException exception) {
        PluginException error = new PluginException(exception.getMessage(), PluginException.PERMISSION_DENIED_ERROR);
        if (mDelegate != null) {
            mDelegate.onError(error);
        }
    }

    protected void showDebugToast (String text) {
        if (mConfig.isDebugging()) {
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }

    public Boolean hasMockLocationsEnabled() {
        return Settings.Secure.getString(mContext.getContentResolver(), android.provider.Settings.Secure.ALLOW_MOCK_LOCATION).equals("1");
    }

    /**
     * Plays debug sound
     * @param name toneGenerator
     */
    protected void playDebugTone (int name) {
        if (toneGenerator == null || !mConfig.isDebugging()) return;

        int duration = 1000;
        toneGenerator.startTone(name, duration);
    }
}