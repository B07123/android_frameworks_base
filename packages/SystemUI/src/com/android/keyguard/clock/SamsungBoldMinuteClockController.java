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
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;
import android.content.Context;
import android.graphics.Typeface;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;
import com.android.internal.util.zeus.ZeusUtils;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

import java.util.TimeZone;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class SamsungBoldMinuteClockController implements ClockPlugin {

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
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Root view of clock.
     */
    private ClockLayout mBigClockView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mClock;

    private Context mContext;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public SamsungBoldMinuteClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor, Context context) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mContext = context;
    }

    private void createViews() {
        mBigClockView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.digital_clock_custom, null);
        mClock = mBigClockView.findViewById(R.id.clock);
        setClockColors();
    }

    private void setClockColors() {
        int mAccentColor = mContext.getResources().getColor(R.color.lockscreen_clock_accent_color);

        if(ZeusUtils.useLockscreenClockMinuteAccentColor(mContext) && ZeusUtils.useLockscreenClockHourAccentColor(mContext)) {
             mClock.setFormat12Hour(Html.fromHtml("<font color=" + mAccentColor + ">hh</font><br><font color=" + mAccentColor + "><strong>mm</strong></font>"));
             mClock.setFormat24Hour(Html.fromHtml("<font color=" + mAccentColor + ">kk</font><br><font color=" + mAccentColor + "><strong>mm</strong></font>"));
        } else if(ZeusUtils.useLockscreenClockHourAccentColor(mContext)) {
             mClock.setFormat12Hour(Html.fromHtml("<font color=" + mAccentColor + ">hh</font><br><strong>mm</strong>"));
             mClock.setFormat24Hour(Html.fromHtml("<font color=" + mAccentColor + ">kk</font><br><strong>mm</strong>"));
        } else if(ZeusUtils.useLockscreenClockMinuteAccentColor(mContext)) {
             mClock.setFormat12Hour(Html.fromHtml("hh<br><font color=" + mAccentColor + "><strong>mm</strong></font>"));
             mClock.setFormat24Hour(Html.fromHtml("kk<br><font color=" + mAccentColor + "><strong>mm</strong></font>"));
        } else {
            mClock.setFormat12Hour(Html.fromHtml("hh<br><strong>mm</strong>"));
            mClock.setFormat24Hour(Html.fromHtml("kk<br><strong>mm</strong>"));
        }
    }

    @Override
    public void onDestroyView() {
        mBigClockView = null;
        mClock = null;
    }

    @Override
    public String getName() {
        return "samsung_bold";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_samsung_bold_minute);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.samsung_bold_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = mLayoutInflater.inflate(R.layout.default_clock_preview, null);
        // TextClock previewTime = previewView.findViewById(R.id.time);
        // previewTime.setFormat12Hour(Html.fromHtml("hh<br><strong>mm</strong>"));
        // previewTime.setFormat24Hour(Html.fromHtml("kk<br><strong>mm</strong>"));
        // TextClock previewDate = previewView.findViewById(R.id.date);

        // // Initialize state of plugin before generating preview.
        // previewTime.setTextColor(Color.WHITE);
        // previewDate.setTextColor(Color.WHITE);
        // ColorExtractor.GradientColors colors = mColorExtractor.getColors(
        //         WallpaperManager.FLAG_LOCK);
        // setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        // onTimeTick();

        return mRenderer.createPreview(previewView, width, height);
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
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        setClockColors();
    }

    @Override
    public void setTypeface(Typeface tf) {
        mClock.setTypeface(tf);
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {}

    @Override
    public void onTimeTick() {
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mBigClockView.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return true;
    }
}
