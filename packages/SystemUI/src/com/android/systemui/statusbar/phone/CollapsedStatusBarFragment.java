/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_CLOCK;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import android.annotation.Nullable;
import android.app.Fragment;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserHandle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.LinearLayout;
import android.provider.Settings;
import android.content.ContentResolver;

import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks,
        StatusBarStateController.StateListener {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";
    public static final String STATUS_BAR_ICON_MANAGER_TAG = "status_bar_icon_manager";
    public static final int FADE_IN_DURATION = 320;
    public static final int FADE_IN_DELAY = 50;
    private PhoneStatusBarView mStatusBar;
    private StatusBarStateController mStatusBarStateController;
    private KeyguardStateController mKeyguardStateController;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private LinearLayout mCustomIconArea;
    private LinearLayout mCenterClockLayout;
    private View mNotificationIconAreaInner;
    private View mCenteredIconArea;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private View mOperatorNameFrame;
    private CommandQueue mCommandQueue;
    private ClockController mClockController;
    private View mStatusBarLogo, mStatusBarLogoRight;

    // Bliss additions
    private View mBatteryBars[] = new View[2];

    private View mCustomCarrierLabel;
    private int mShowCarrierLabel;
    private boolean mHasCarrierLabel;

    private ContentResolver resolver;

    // Default Dualstatusbar 
    private boolean mDualStatusbarEnabled;
    private int mDualStatusbarMode;
    
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

         void observe() {
             resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_CARRIER),
                    false, this, UserHandle.USER_ALL);
        }

         @Override
        public void onChange(boolean selfChange) {
            updateSettings(true);
        }
    }

    private SettingsObserver mSettingsObserver;

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mCommandQueue.recomputeDisableFlags(getContext().getDisplayId(), true /* animate */);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mKeyguardStateController = Dependency.get(KeyguardStateController.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarComponent = Dependency.get(StatusBar.class);
        mCommandQueue = Dependency.get(CommandQueue.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        mDualStatusbarEnabled = getResources().getBoolean(com.android.internal.R.bool.config_default_dual_status_bar);
        mDualStatusbarMode = getResources().getInteger(com.android.internal.R.integer.config_default_dual_status_bar_mode);
        int mDSBarmode = 0;
        int mDualStatusbarModeHelper = Settings.System.getInt(getContext().getContentResolver(),
        Settings.System.DUAL_STATUSBAR_ROW_MODE_HELPER, 0);
        if(mDualStatusbarEnabled && mDualStatusbarModeHelper == 0) {
            mDSBarmode = mDualStatusbarMode;
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.DUAL_STATUSBAR_ROW_MODE_HELPER, 1);
        } else {
            mDSBarmode = getDualStatusbarMode();
        }
        switch(mDSBarmode) {
             case 1:
                Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.DUAL_STATUSBAR_ROW_MODE, 1);
               return inflater.inflate(R.layout.status_bar_dual, container, false);
             case 2:
                Settings.System.putInt(getContext().getContentResolver(),
                        Settings.System.DUAL_STATUSBAR_ROW_MODE, 2);
                return inflater.inflate(R.layout.status_bar_dual_network_speed, container, false);
             case 3:
                Settings.System.putInt(getContext().getContentResolver(),
                        Settings.System.DUAL_STATUSBAR_ROW_MODE, 3);
                return inflater.inflate(R.layout.status_bar_dual_used_data, container, false);
            default:
                Settings.System.putInt(getContext().getContentResolver(),
                        Settings.System.DUAL_STATUSBAR_ROW_MODE, 4);
                return inflater.inflate(R.layout.status_bar, container, false);
         }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.restoreHierarchyState(
                    savedInstanceState.getSparseParcelableArray(EXTRA_PANEL_STATE));
        }
        resolver = getContext().getContentResolver();
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons),
                Dependency.get(CommandQueue.class));
        mDarkIconManager.setShouldLog(true);
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mCustomIconArea = mStatusBar.findViewById(R.id.left_icon_area);
        mCenterClockLayout = (LinearLayout) mStatusBar.findViewById(R.id.center_clock_layout);
        mClockController = new ClockController(getContext(), mStatusBar);
        mBatteryBars[0] = mStatusBar.findViewById(R.id.battery_bar);
        mBatteryBars[1] = mStatusBar.findViewById(R.id.battery_bar_1);
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);
        mStatusBarLogo = mStatusBar.findViewById(R.id.statusbar_logo);
        mStatusBarLogoRight = mStatusBar.findViewById(R.id.statusbar_logo_right);
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();
        initOperatorName();
        updateSettings(false);
    }

    private int getDualStatusbarMode() {
        return Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.DUAL_STATUSBAR_ROW_MODE, 0);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SparseArray<Parcelable> states = new SparseArray<>();
        mStatusBar.saveHierarchyState(states);
        outState.putSparseParcelableArray(EXTRA_PANEL_STATE, states);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCommandQueue.addCallback(this);
        mStatusBarStateController.addCallback(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCommandQueue.removeCallback(this);
        mStatusBarStateController.removeCallback(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
    }

    public void initNotificationIconArea(NotificationIconAreaController
            notificationIconAreaController) {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                notificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);

        ViewGroup statusBarCenteredIconArea = mStatusBar.findViewById(R.id.centered_icon_area);
        mCenteredIconArea = notificationIconAreaController.getCenteredNotificationAreaView();
        if (mCenteredIconArea.getParent() != null) {
            ((ViewGroup) mCenteredIconArea.getParent())
                    .removeView(mCenteredIconArea);
        }
        statusBarCenteredIconArea.addView(mCenteredIconArea);

        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int displayId, int state1, int state2, boolean animate) {
        if (displayId != getContext().getDisplayId()) {
            return;
        }
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
                hideOperatorName(animate);
            } else {
                showSystemIconArea(animate);
                showOperatorName(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
                hideCarrierName(animate);
            } else {
                showNotificationIconArea(animate);
                showCarrierName(animate);
            }
        }
    }

    protected int adjustDisableFlags(int state) {
        boolean headsUpVisible = mStatusBarComponent.headsUpShouldBeVisible();
        if (headsUpVisible) {
            View clockView = mClockController.getClock();
            if (clockView == null) {
                state |= DISABLE_CLOCK;
            } else {
                boolean isRightClock = clockView.getId() == R.id.clock_right;
                if (!isRightClock) {
                    state |= DISABLE_CLOCK;
                }
            }
        }

        if (!mKeyguardStateController.isLaunchTransitionFadingAway()
                && !mKeyguardStateController.isKeyguardFadingAway()
                && shouldHideNotificationIcons()
                && !(mStatusBarStateController.getState() == StatusBarState.KEYGUARD
                        && headsUpVisible)) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
            state |= DISABLE_CLOCK;
        }


        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }

        // The shelf will be hidden when dozing with a custom clock, we must show notification
        // icons in this occasion.
        if (mStatusBarStateController.isDozing()
                && mStatusBarComponent.getPanelController().hasCustomClock()) {
            state |= DISABLE_CLOCK | DISABLE_SYSTEM_INFO;
        }

        return state;
    }

    private boolean shouldHideNotificationIcons() {
        if (!mStatusBar.isClosed() && mStatusBarComponent.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        if (mStatusBarComponent.hideStatusBarIconsForBouncer()) {
            return true;
        }
        return false;
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        for (View batteryBar: mBatteryBars) {
            animateHide(batteryBar, animate);
        }
        animateHide(mStatusBarLogoRight, animate);
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        for (View batteryBar: mBatteryBars) {
            animateShow(batteryBar, animate);
        }
        animateShow(mStatusBarLogoRight, animate);
    }

    /**
     * If panel is expanded/expanding it usually means QS shade is opening, so
     * don't set the clock GONE otherwise it'll mess up the animation.
     */
    private int clockHiddenMode() {
        if (!mStatusBar.isClosed() && !mKeyguardStateController.isShowing()
                && !mStatusBarStateController.isDozing()
                && mClockController.getClock().shouldBeVisible()) {
            return View.INVISIBLE;
        }
        return View.GONE;
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate);
        animateHide(mCenteredIconArea, animate);
        animateHide(mCustomIconArea, animate);
        animateHide(mCenterClockLayout, animate);
        animateHide(mStatusBarLogo, animate);
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
        animateShow(mCenteredIconArea, animate);
        animateShow(mCustomIconArea, animate);
        animateShow(mCenterClockLayout, animate);
        animateShow(mStatusBarLogo, animate);
    }

    public void hideOperatorName(boolean animate) {
        if (mOperatorNameFrame != null) {
            animateHide(mOperatorNameFrame, animate);
        }
    }

    public void showOperatorName(boolean animate) {
        if (mOperatorNameFrame != null) {
            animateShow(mOperatorNameFrame, animate);
        }
    }

    public void hideCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            animateHiddenState(mCustomCarrierLabel, View.GONE, animate);
        }
    }

    public void showCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            setCarrierLabel(animate);
        }
    }

    /**
     * Animate a view to INVISIBLE or GONE
     */
    private void animateHiddenState(final View v, int state, boolean animate) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(state);
            return;
        }

        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(state));
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate) {
        if (v.getVisibility() == View.GONE)
            return;
        animateHiddenState(v, View.INVISIBLE, animate);
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        if (v.getVisibility() == View.GONE)
            return;
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(FADE_IN_DURATION)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(FADE_IN_DELAY)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardStateController.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardStateController.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardStateController.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

    private void initOperatorName() {
        if (getResources().getBoolean(R.bool.config_showOperatorNameInStatusBar)) {
            ViewStub stub = mStatusBar.findViewById(R.id.operator_name);
            mOperatorNameFrame = stub.inflate();
        }
    }

    @Override
    public void onStateChanged(int newState) {

    }

    @Override
    public void onDozingChanged(boolean isDozing) {
        disable(getContext().getDisplayId(), mDisabled1, mDisabled1, false /* animate */);
    }

    public void updateSettings(boolean animate) {
        mShowCarrierLabel = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_CARRIER, 1,
                UserHandle.USER_CURRENT);
        mHasCarrierLabel = (mShowCarrierLabel == 2 || mShowCarrierLabel == 3);
        setCarrierLabel(animate);
   }

    private void setCarrierLabel(boolean animate) {
        if (mHasCarrierLabel) {
            animateShow(mCustomCarrierLabel, animate);
        } else {
            animateHiddenState(mCustomCarrierLabel, View.GONE, animate);
        }
    }
}
