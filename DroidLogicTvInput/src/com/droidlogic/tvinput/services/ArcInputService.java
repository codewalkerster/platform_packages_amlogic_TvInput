/*
 * Copyright (c) 2014 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description: JAVA file
 */

package com.droidlogic.tvinput.services;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import com.droidlogic.tvinput.Utils;

import com.droidlogic.app.SystemControlManager;
import com.droidlogic.app.tv.DroidLogicTvUtils;
import com.droidlogic.tvinput.R;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.media.tv.TvInputHardwareInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvStreamConfig;
import android.media.tv.TvInputManager.Hardware;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import java.util.HashMap;
import java.util.Map;
import android.view.Surface;
import android.net.Uri;
import android.media.tv.TvInputManager;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiHotplugEvent;


public class ArcInputService extends DroidLogicTvInputService {
    private static final String TAG = ArcInputService.class.getSimpleName();
    private static final String SYS_NODE_EARC = "/sys/class/extcon/earcrx/state";

    private ArcInputSession mCurrentSession;
    private int id = 0;
    private Map<Integer, ArcInputSession> sessionMap = new HashMap<>();
    private SystemControlManager mSystemControlManager;
    private HdmiControlManager mHdmiControlManager;

    private HdmiControlManager.HotplugEventListener mHotplugListener;
    private boolean mIsMain;

    private static final long DELAY_GO_HOME = 5000;
    private Handler mHandler = new Handler();
    private Runnable mRunnableGoHome = ()->{
        Utils.logd(TAG, "Go to launcher");
        try {
            Intent activityIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            getApplicationContext().startActivity(activityIntent);
        } catch (Exception e) {
            Utils.loge(TAG, "Can't find activity to switch to HOME" + e);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        initInputService(DroidLogicTvUtils.DEVICE_ID_ARC, ArcInputService.class.getName());

        try {
            mHdmiControlManager = (HdmiControlManager) getApplicationContext().getSystemService(Context.HDMI_CONTROL_SERVICE);
        } catch (Exception e) {
            Utils.loge(TAG, "failed to get hdmi control manager:" + e);
        }
        if (mHdmiControlManager != null) {
            mHotplugListener= (HdmiHotplugEvent event)->{
                Utils.logd(TAG, "Hotplug " + event);
                if (event.getPort() == 0) {
                    mHandler.removeCallbacks(mRunnableGoHome);
                    if (!event.isConnected()) {
                        mHandler.postDelayed(mRunnableGoHome, DELAY_GO_HOME);
                    }
                }
            };
            mHdmiControlManager.addHotplugEventListener(mHotplugListener);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHdmiControlManager != null) {
            mHdmiControlManager.removeHotplugEventListener(mHotplugListener);
        }
    }

    @Override
    public Session onCreateSession(String inputId) {
        super.onCreateSession(inputId);
        Utils.logd(TAG, "onCreateSession:"+inputId);
        mCurrentSession = new ArcInputSession(this, inputId, getHardwareDeviceId(inputId));
        mCurrentSession.setSessionId(id);
        registerInputSession(mCurrentSession);
        sessionMap.put(id, mCurrentSession);
        id++;
        if (mSystemControlManager == null) {
            mSystemControlManager =  SystemControlManager.getInstance();
        }
        return mCurrentSession;
    }

    @Override
    public void setCurrentSessionById(int sessionId) {
        Utils.logd(TAG, "setCurrentSessionById:"+sessionId);
        ArcInputSession session = sessionMap.get(sessionId);
        if (session != null) {
            mCurrentSession = session;
        }
    }

    @Override
    public void doTuneFinish(int result, Uri uri, int sessionId) {
        Utils.logd(TAG, "doTuneFinish,result:"+result+"sessionId:"+sessionId);
        if (result == ACTION_SUCCESS) {
            ArcInputSession session = sessionMap.get(sessionId);
            if (session != null) {
                if (TextUtils.isEmpty(mSystemControlManager.readSysFs(SYS_NODE_EARC))) {
                    session.setParameters("spdifin/arcin switch=1");
                }
                //notifyVideoUnavailable for cts test
                session.notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
            }
        }
    }

    public class ArcInputSession extends TvInputBaseSession {
        public ArcInputSession(Context context, String inputId, int deviceId) {
            super(context, inputId, deviceId);
            Utils.logd(TAG, "=====new ArcInputSession=====");
            //initOverlayView(R.layout.layout_overlay);
            if (mOverlayView != null) {
                mOverlayView.setImage(R.drawable.spdifin);
            }
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            super.onSetSurface(surface);
            return setSurfaceInService(surface,this);
        }
        @Override
        public boolean onTune(Uri channelUri) {
            return doTuneInService(channelUri, getSessionId());
        }
        @Override
        public void doRelease() {
            if (sessionMap.containsKey(getSessionId())) {
                sessionMap.remove(getSessionId());
                if (mCurrentSession == this) {
                    mCurrentSession = null;
                    registerInputSession(null);
                }
            }

            super.doRelease();
        }

        @Override
        public void doAppPrivateCmd(String action, Bundle bundle) {
            super.doAppPrivateCmd(action, bundle);
            if (TextUtils.equals(DroidLogicTvUtils.ACTION_STOP_TV, action)) {
                if (mHardware != null) {
                    mHardware.setSurface(null, null);
                }
            }
        }

        @Override
        public void onSetMain(boolean isMain) {
            super.onSetMain(isMain);
            mIsMain = isMain;
        }

    }

    public String getDeviceClassName() {
        return ArcInputService.class.getName();
    }

    public int getDeviceSourceType() {
        return DroidLogicTvUtils.DEVICE_ID_ARC;
    }
}
