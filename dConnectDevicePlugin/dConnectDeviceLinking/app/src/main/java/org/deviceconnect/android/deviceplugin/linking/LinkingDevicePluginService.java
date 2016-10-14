/*
 LinkingDeviceService.java
 Copyright (c) 2016 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.linking;

import android.content.Intent;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.linking.beacon.LinkingBeaconManager;
import org.deviceconnect.android.deviceplugin.linking.beacon.LinkingBeaconUtil;
import org.deviceconnect.android.deviceplugin.linking.beacon.data.LinkingBeacon;
import org.deviceconnect.android.deviceplugin.linking.beacon.profile.BeaconUtil;
import org.deviceconnect.android.deviceplugin.linking.beacon.service.LinkingBeaconService;
import org.deviceconnect.android.deviceplugin.linking.linking.LinkingDevice;
import org.deviceconnect.android.deviceplugin.linking.linking.LinkingDeviceManager;
import org.deviceconnect.android.deviceplugin.linking.linking.service.LinkingDeviceService;
import org.deviceconnect.android.deviceplugin.linking.profile.LinkingServiceDiscoveryProfile;
import org.deviceconnect.android.deviceplugin.linking.profile.LinkingSystemProfile;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.event.cache.MemoryCacheController;
import org.deviceconnect.android.message.DConnectMessageService;
import org.deviceconnect.android.profile.DeviceOrientationProfile;
import org.deviceconnect.android.profile.KeyEventProfile;
import org.deviceconnect.android.profile.ProximityProfile;
import org.deviceconnect.android.profile.SystemProfile;
import org.deviceconnect.android.service.DConnectService;

import java.util.List;

/**
 * Linking device plug-in.
 *
 * @author NTT DOCOMO, INC.
 */
public class LinkingDevicePluginService extends DConnectMessageService {

    private static final String TAG = "LinkingPlugIn";

    @Override
    public void onCreate() {
        super.onCreate();
        EventManager.INSTANCE.setController(new MemoryCacheController());

        createLinkingDeviceList();
        createLinkingBeaconList();

        addProfile(new LinkingServiceDiscoveryProfile(this, getServiceProvider()));
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (LinkingBeaconUtil.ACTION_BEACON_SCAN_RESULT.equals(action) ||
                    LinkingBeaconUtil.ACTION_BEACON_SCAN_STATE.equals(action)) {
                LinkingApplication app = (LinkingApplication)  getApplication();
                LinkingBeaconManager mgr = app.getLinkingBeaconManager();
                try {
                    mgr.onReceivedBeacon(intent);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "", e);
                    }
                }
                return START_STICKY;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected SystemProfile getSystemProfile() {
        return new LinkingSystemProfile();
    }

    @Override
    public void onManagerTerminated() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onManagerTerminated");
        }
        ((LinkingApplication) getApplication()).resetManager();
        removeAllServices();
    }

    @Override
    public void onManagerEventTransmitDisconnected(final String sessionKey) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onManagerEventTransmitDisconnected: " + sessionKey);
        }
        cleanupSession(sessionKey);
    }

    @Override
    public void onDevicePluginReset() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onDevicePluginReset");
        }
        ((LinkingApplication) getApplication()).resetManager();
        resetService();
    }

    @Override
    public void onManagerUninstalled() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onManagerUninstalled");
        }
        ((LinkingApplication) getApplication()).resetManager();
        removeAllServices();
    }

    public void cleanupSession(final String sessionKey) {
        if (sessionKey == null) {
            return;
        }

        List<Event> events = EventManager.INSTANCE.getEventList(sessionKey);
        for (Event event : events) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "event=" + event);
            }
            EventManager.INSTANCE.removeEvent(event);
            stopDeviceOrientation(event);
            stopProximity(event);
            stopKeyEvent(event);
        }
        stopBeacon();
    }

    public void refreshDevices() {
        createLinkingDeviceList();
        createLinkingBeaconList();
        cleanupDConnectService();
    }

    private void createLinkingDeviceList() {
        for (LinkingDevice device : getLinkingDeviceManager().getDevices()) {
            DConnectService service = findDConnectService(device.getBdAddress());
            if (service == null) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Added Device: " + device.getDisplayName());
                }
                getServiceProvider().addService(new LinkingDeviceService(this, device));
            } else {
                ((LinkingDeviceService) service).setLinkingDevice(device);
            }
        }
    }

    private void createLinkingBeaconList() {
        for (LinkingBeacon beacon : getLinkingBeaconManager().getLinkingBeacons()) {
            DConnectService service = findDConnectService(beacon.getServiceId());
            if (service == null) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Added Beacon: " + beacon.getDisplayName());
                }
                getServiceProvider().addService(new LinkingBeaconService(this, beacon));
            } else {
                ((LinkingBeaconService) service).setLinkingBeacon(beacon);
            }
        }
    }

    private void cleanupDConnectService() {
        for (DConnectService service : getServiceProvider().getServiceList()) {
            if (!containsLinkingDevices(service.getId()) && !containsLinkingBeacons(service.getId())) {
                removeService(service);
            }
        }
    }

    private void removeAllServices() {
        List<DConnectService> services = getServiceProvider().getServiceList();
        for (DConnectService service : services) {
            removeService(service);
        }
    }

    private void removeService(final DConnectService service) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Remove Service: " + service.getName());
        }

        if (service instanceof LinkingDeviceService) {
            ((LinkingDeviceService) service).destroy();
        } else if (service instanceof LinkingBeaconService) {
            ((LinkingBeaconService) service).destroy();
        }
        getServiceProvider().removeService(service);
    }

    private boolean containsLinkingDevices(final String id) {
        for (LinkingDevice device : getLinkingDeviceManager().getDevices()) {
            if (id.equals(device.getBdAddress())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLinkingBeacons(final String id) {
        for (LinkingBeacon beacon : getLinkingBeaconManager().getLinkingBeacons()) {
            if (id.equals(beacon.getServiceId())) {
                return true;
            }
        }
        return false;
    }

    private DConnectService findDConnectService(final String id) {
        for (DConnectService service : getServiceProvider().getServiceList()) {
            if (service.getId().equals(id)) {
                return service;
            }
        }
        return null;
    }

    private void resetService() {
        removeAllServices();
        createLinkingDeviceList();
        createLinkingBeaconList();
    }

    private void stopDeviceOrientation(final Event event) {
        if (!DeviceOrientationProfile.PROFILE_NAME.equals(event.getProfile()) ||
                !DeviceOrientationProfile.ATTRIBUTE_ON_DEVICE_ORIENTATION.equals(event.getAttribute())) {
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "stopDeviceOrientation");
        }

        LinkingDeviceManager mgr = getLinkingDeviceManager();
        String serviceId = event.getServiceId();
        List<Event> events = EventManager.INSTANCE.getEventList(
                serviceId, DeviceOrientationProfile.PROFILE_NAME, null,
                DeviceOrientationProfile.ATTRIBUTE_ON_DEVICE_ORIENTATION);
        if (events.isEmpty()) {
            mgr.stopSensor(mgr.findDeviceByBdAddress(serviceId));
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "events=" + events.size());
            }
        }
    }

    private void stopProximity(final Event event) {
        if (!ProximityProfile.PROFILE_NAME.equals(event.getProfile()) ||
                !ProximityProfile.ATTRIBUTE_ON_DEVICE_PROXIMITY.equals(event.getAttribute())) {
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "stopProximity");
        }

        LinkingDeviceManager mgr = getLinkingDeviceManager();
        String serviceId = event.getServiceId();
        List<Event> events = EventManager.INSTANCE.getEventList(
                serviceId, ProximityProfile.PROFILE_NAME, null,
                ProximityProfile.ATTRIBUTE_ON_DEVICE_PROXIMITY);
        if (events.isEmpty()) {
            mgr.stopRange(mgr.findDeviceByBdAddress(serviceId));
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "events=" + events.size());
            }
        }
    }

    private void stopKeyEvent(final Event event) {
        if (!KeyEventProfile.PROFILE_NAME.equals(event.getProfile()) ||
                !KeyEventProfile.ATTRIBUTE_ON_DOWN.equals(event.getAttribute())) {
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "stopKeyEvent");
        }

        LinkingDeviceManager mgr = getLinkingDeviceManager();
        String serviceId = event.getServiceId();

        List<Event> events = EventManager.INSTANCE.getEventList(
                serviceId, KeyEventProfile.PROFILE_NAME, null, KeyEventProfile.ATTRIBUTE_ON_DOWN);
        if (events.isEmpty()) {
            mgr.stopKeyEvent(mgr.findDeviceByBdAddress(serviceId));
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "events=" + events.size());
            }
        }
    }

    private void stopBeacon() {
        if (BeaconUtil.isEmptyEvent(getLinkingBeaconManager())) {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "stop beacon");
            }
            getLinkingBeaconManager().stopBeaconScan();
        }
    }

    private LinkingDeviceManager getLinkingDeviceManager() {
        LinkingApplication app = getLinkingApplication();
        return app.getLinkingDeviceManager();
    }

    private LinkingBeaconManager getLinkingBeaconManager() {
        LinkingApplication app = getLinkingApplication();
        return app.getLinkingBeaconManager();
    }

    private LinkingApplication getLinkingApplication() {
        return (LinkingApplication) getApplication();
    }
}
