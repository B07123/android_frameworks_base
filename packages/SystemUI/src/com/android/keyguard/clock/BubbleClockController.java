/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;
import android.graphics.Typeface;

import android.content.Context;
import com.android.internal.util.zeus.ZeusUtils;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

import java.util.TimeZone;

/**
 * Controller for Bubble clock that can appear on lock screen and AOD.
 */
public class BubbleClockController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Computes preferred position of clock.
     */
    private final SmallClockPosition mClockPosition;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Custom clock shown on AOD screen and behind stack scroller on lock.
     */
    private ClockLayout mBigClockView;
    private ImageClock mAnalogClock;

    /**
     * Small clock shown on lock screen above stack scroller.
     */
    private View mLockClockContainer;
    private TextClock mLockClock;

    /**
     * Helper to extract colors from wallpaper palette for clock face.
     */
    private final ClockPalette mPalette = new ClockPalette();

    private Context mContext;

    /**
     * Create a BubbleClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public BubbleClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor, Context context) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mContext = context;
        mClockPosition = new SmallClockPosition(res);
    }

    private void createViews() {
        mBigClockView = (ClockLayout) mLayoutInflater.inflate(R.layout.bubble_clock, null);
        mAnalogClock = (ImageClock) mBigClockView.findViewById(R.id.analog_clock);

        mLockClockContainer = mLayoutInflater.inflate(R.layout.digital_clock, null);
        mLockClock = (TextClock) mLockClockContainer.findViewById(R.id.lock_screen_clock);
    }

    @Override
    public void onDestroyView() {
        mBigClockView = null;
        mAnalogClock = null;
        mLockClockContainer = null;
        mLockClock = null;
    }

    @Override
    public String getName() {
        return "bubble";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_bubble);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.bubble_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        // Use the big clock view for the preview
        View view = getBigClockView();

        // // Initialize state of plugin before generating preview.
        // setDarkAmount(1f);
        // setTextColor(Color.WHITE);
        // ColorExtractor.GradientColors colors = mColorExtractor.getColors(
        //         WallpaperManager.FLAG_LOCK);
        // setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        // onTimeTick();

        return mRenderer.createPreview(view, width, height);
    }

    @Override
    public View getView() {
        return null;
    }

    @Override
    public View getBigClockView() {
        if (mBigClockView == null) {
            createViews();
        }
        return mBigClockView;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return CLOCK_USE_DEFAULT_Y;
    }

    @Override
    public void setTextColor(int color) {
        updateColor();
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        mPalette.setColorPalette(supportsDarkText, colorPalette);
        updateColor();
    }

    private void updateColor() {
        final int primary = mPalette.getPrimaryColor();

        if(ZeusUtils.useLockscreenCustomClockAccentColor(mContext)) {
            mAnalogClock.setClockColors(primary, mContext.getResources().getColor(R.color.lockscreen_clock_accent_color));
        } else {
            final int secondary = mPalette.getSecondaryColor();
            mAnalogClock.setClockColors(primary, secondary);
        }
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mPalette.setDarkAmount(darkAmount);
        mClockPosition.setDarkAmount(darkAmount);
        mBigClockView.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeTick() {
        mAnalogClock.onTimeChanged();
        mBigClockView.onTimeChanged();
        mLockClock.refreshTime();
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {
        mAnalogClock.onTimeZoneChanged(timeZone);
    }

    @Override
    public boolean shouldShowStatusArea() {
        return true;
    }

    @Override
    public boolean shouldShowInBigContainer() {
        return true;
    }
}
