/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.view.View;

import com.android.systemui.R;

public final class PhoneStatusBarTransitions extends BarTransitions {
    private static final float ICON_ALPHA_WHEN_NOT_OPAQUE = 1;
    private static final float ICON_ALPHA_WHEN_LIGHTS_OUT_BATTERY_CLOCK = 0.5f;
    private static final float ICON_ALPHA_WHEN_LIGHTS_OUT_NON_BATTERY_CLOCK = 0;

    private final float mIconAlphaWhenOpaque;

    private View mLeftSide, mStatusIcons, mBattery, mStatusBarLogo, mStatusBarLogoRight;
    private View mBatteryBars[] = new View[2];

    private Animator mCurrentAnimation;

    /**
     * @param backgroundView view to apply the background drawable
     */
    public PhoneStatusBarTransitions(PhoneStatusBarView statusBarView, View backgroundView) {
        super(backgroundView, R.drawable.status_background);
        final Resources res = statusBarView.getContext().getResources();
        mIconAlphaWhenOpaque = res.getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        mLeftSide = statusBarView.findViewById(R.id.status_bar_left_side);
        mStatusIcons = statusBarView.findViewById(R.id.statusIcons);
        mBattery = statusBarView.findViewById(R.id.battery);
        mBatteryBars[0] = statusBarView.findViewById(R.id.battery_bar);
        mBatteryBars[1] = statusBarView.findViewById(R.id.battery_bar_1);
        mStatusBarLogo = statusBarView.findViewById(R.id.statusbar_logo);
        mStatusBarLogoRight = statusBarView.findViewById(R.id.statusbar_logo_right);
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyMode(getMode(), false /*animate*/);
    }

    public ObjectAnimator animateTransitionTo(View v, float toAlpha) {
        return ObjectAnimator.ofFloat(v, "alpha", v.getAlpha(), toAlpha);
    }

    private float getNonBatteryClockAlphaFor(int mode) {
        return isLightsOut(mode) ? ICON_ALPHA_WHEN_LIGHTS_OUT_NON_BATTERY_CLOCK
                : !isOpaque(mode) ? ICON_ALPHA_WHEN_NOT_OPAQUE
                : mIconAlphaWhenOpaque;
    }

    private float getBatteryClockAlpha(int mode) {
        return isLightsOut(mode) ? ICON_ALPHA_WHEN_LIGHTS_OUT_BATTERY_CLOCK
                : getNonBatteryClockAlphaFor(mode);
    }

    private boolean isOpaque(int mode) {
        return !(mode == MODE_SEMI_TRANSPARENT || mode == MODE_TRANSLUCENT
                || mode == MODE_TRANSPARENT || mode == MODE_LIGHTS_OUT_TRANSPARENT);
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyMode(newMode, animate);
    }

    private void applyMode(int mode, boolean animate) {
        if (mLeftSide == null) return; // pre-init
        float newAlpha = getNonBatteryClockAlphaFor(mode);
        float newAlphaBC = getBatteryClockAlpha(mode);
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
        }
        if (animate) {
            AnimatorSet anims = new AnimatorSet();
            anims.playTogether(
                    animateTransitionTo(mStatusBarLogo, newAlpha),
                    animateTransitionTo(mStatusBarLogoRight, newAlpha),
                    animateTransitionTo(mLeftSide, newAlpha),
                    animateTransitionTo(mStatusIcons, newAlpha),
                    animateTransitionTo(mBattery, newAlphaBC),
                    animateTransitionTo(mBatteryBars[0], newAlphaBC),
                    animateTransitionTo(mBatteryBars[1], newAlphaBC)
                    );
            if (isLightsOut(mode)) {
                anims.setDuration(LIGHTS_OUT_DURATION);
            }
            anims.start();
            mCurrentAnimation = anims;
        } else {
            mStatusBarLogo.setAlpha(newAlpha);
            mStatusBarLogoRight.setAlpha(newAlpha);
            mLeftSide.setAlpha(newAlpha);
            mStatusIcons.setAlpha(newAlpha);
            mBattery.setAlpha(newAlphaBC);
            mBatteryBars[0].setAlpha(newAlphaBC);
            mBatteryBars[1].setAlpha(newAlphaBC);
        }
    }
}
