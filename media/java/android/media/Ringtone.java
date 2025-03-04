/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.media;

import android.annotation.Nullable;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.Settings;
import android.media.AudioDeviceInfo;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Ringtone provides a quick method for playing a ringtone, notification, or
 * other similar types of sounds.
 * <p>
 * For ways of retrieving {@link Ringtone} objects or to show a ringtone
 * picker, see {@link RingtoneManager}.
 *
 * @see RingtoneManager
 */
public class Ringtone {
    private static final String TAG = "Ringtone";
    private static final boolean LOGD = false;

    private static final String[] MEDIA_COLUMNS = new String[] {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE
    };
    /** Selection that limits query results to just audio files */
    private static final String MEDIA_SELECTION = MediaColumns.MIME_TYPE + " LIKE 'audio/%' OR "
            + MediaColumns.MIME_TYPE + " IN ('application/ogg', 'application/x-flac')";

    // keep references on active Ringtones until stopped or completion listener called.
    private static final ArrayList<Ringtone> sActiveRingtones = new ArrayList<Ringtone>();

    private final Context mContext;
    private final AudioManager mAudioManager;
    private VolumeShaper.Configuration mVolumeShaperConfig;
    private VolumeShaper mVolumeShaper;

    /**
     * Flag indicating if we're allowed to fall back to remote playback using
     * {@link #mRemotePlayer}. Typically this is false when we're the remote
     * player and there is nobody else to delegate to.
     */
    private final boolean mAllowRemote;
    private final IRingtonePlayer mRemotePlayer;
    private final Binder mRemoteToken;

    @UnsupportedAppUsage
    private MediaPlayer mLocalPlayer;
    private final MyOnCompletionListener mCompletionListener = new MyOnCompletionListener();

    @UnsupportedAppUsage
    private Uri mUri;
    private String mTitle;

    private AudioAttributes mFinalAudioAttributes;

    private AudioAttributes mAudioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private AudioAttributes mAudioAttributesHeadset = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    // playback properties, use synchronized with mPlaybackSettingsLock
    private boolean mIsLooping = false;
    private float mVolume = 1.0f;
    private final Object mPlaybackSettingsLock = new Object();

    /** {@hide} */
    @UnsupportedAppUsage
    public Ringtone(Context context, boolean allowRemote) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAllowRemote = allowRemote;
        mRemotePlayer = allowRemote ? mAudioManager.getRingtonePlayer() : null;
        mRemoteToken = allowRemote ? new Binder() : null;
    }

    /**
     * Sets the stream type where this ringtone will be played.
     *
     * @param streamType The stream, see {@link AudioManager}.
     * @deprecated use {@link #setAudioAttributes(AudioAttributes)}
     */
    @Deprecated
    public void setStreamType(int streamType) {
        PlayerBase.deprecateStreamTypeForPlayback(streamType, "Ringtone", "setStreamType()");
        setAudioAttributes(new AudioAttributes.Builder()
                .setInternalLegacyStreamType(streamType)
                .build());
    }

    /**
     * Gets the stream type where this ringtone will be played.
     *
     * @return The stream type, see {@link AudioManager}.
     * @deprecated use of stream types is deprecated, see
     *     {@link #setAudioAttributes(AudioAttributes)}
     */
    @Deprecated
    public int getStreamType() {
        return AudioAttributes.toLegacyStreamType(mAudioAttributes);
    }

    /**
     * Sets the {@link AudioAttributes} for this ringtone.
     * @param attributes the non-null attributes characterizing this ringtone.
     */
    public void setAudioAttributes(AudioAttributes attributes)
            throws IllegalArgumentException {
        if (attributes == null) {
            throw new IllegalArgumentException("Invalid null AudioAttributes for Ringtone");
        }
        mAudioAttributes = attributes;
        // The audio attributes have to be set before the media player is prepared.
        // Re-initialize it.
        setUri(mUri, mVolumeShaperConfig);
    }

    /**
     * Returns the {@link AudioAttributes} used by this object.
     * @return the {@link AudioAttributes} that were set with
     *     {@link #setAudioAttributes(AudioAttributes)} or the default attributes if none were set.
     */
    public AudioAttributes getAudioAttributes() {
        return mAudioAttributes;
    }

    /**
     * Sets the player to be looping or non-looping.
     * @param looping whether to loop or not.
     */
    public void setLooping(boolean looping) {
        synchronized (mPlaybackSettingsLock) {
            mIsLooping = looping;
            applyPlaybackProperties_sync();
        }
    }

    /**
     * Returns whether the looping mode was enabled on this player.
     * @return true if this player loops when playing.
     */
    public boolean isLooping() {
        synchronized (mPlaybackSettingsLock) {
            return mIsLooping;
        }
    }

    /**
     * Sets the volume on this player.
     * @param volume a raw scalar in range 0.0 to 1.0, where 0.0 mutes this player, and 1.0
     *   corresponds to no attenuation being applied.
     */
    public void setVolume(float volume) {
        synchronized (mPlaybackSettingsLock) {
            if (volume < 0.0f) { volume = 0.0f; }
            if (volume > 1.0f) { volume = 1.0f; }
            mVolume = volume;
            applyPlaybackProperties_sync();
        }
    }

    /**
     * Returns the volume scalar set on this player.
     * @return a value between 0.0f and 1.0f.
     */
    public float getVolume() {
        synchronized (mPlaybackSettingsLock) {
            return mVolume;
        }
    }

    /**
     * Must be called synchronized on mPlaybackSettingsLock
     */
    private void applyPlaybackProperties_sync() {
        if (mLocalPlayer != null) {
            mLocalPlayer.setVolume(mVolume);
            mLocalPlayer.setLooping(mIsLooping);
        } else if (mAllowRemote && (mRemotePlayer != null)) {
            try {
                mRemotePlayer.setPlaybackProperties(mRemoteToken, mVolume, mIsLooping);
            } catch (RemoteException e) {
                Log.w(TAG, "Problem setting playback properties: ", e);
            }
        } else {
            Log.w(TAG,
                    "Neither local nor remote player available when applying playback properties");
        }
    }

    /**
     * Returns a human-presentable title for ringtone. Looks in media
     * content provider. If not in either, uses the filename
     *
     * @param context A context used for querying.
     */
    public String getTitle(Context context) {
        if (mTitle != null) return mTitle;
        return mTitle = getTitle(context, mUri, true /*followSettingsUri*/, mAllowRemote);
    }

        public void setCustomAudioAttributes() {
        int focusmode = getRingtoneFocusMode();
        boolean isHeadsetConnected = false;
        AudioDeviceInfo[] connectedDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : connectedDevices) {
            if (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                isHeadsetConnected = true;
                break;
            }
        }
        switch (focusmode) {
            case 0: //play ringtone only from headset if music playing, otherwise from speakerphone
                if (isHeadsetConnected && mAudioManager.isMusicActive()) {
                    mFinalAudioAttributes = mAudioAttributesHeadset;
                } else {
                    mFinalAudioAttributes = mAudioAttributes;
                }
                break;
            default:
            case 1: //aosp behavior, ringtone always from both headset and speakerphone
                    mFinalAudioAttributes = mAudioAttributes;
                break;
        }
    }

    private int getRingtoneFocusMode() {
        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.RINGTONE_FOCUS_MODE, 1);
        return mode;
    }

    /**
     * @hide
     */
    public static String getTitle(
            Context context, Uri uri, boolean followSettingsUri, boolean allowRemote) {
        ContentResolver res = context.getContentResolver();

        String title = null;

        if (uri != null) {
            String authority = ContentProvider.getAuthorityWithoutUserId(uri.getAuthority());

            if (Settings.AUTHORITY.equals(authority)) {
                if (followSettingsUri) {
                    Uri actualUri = RingtoneManager.getActualDefaultRingtoneUri(context,
                            RingtoneManager.getDefaultType(uri));
                    String actualTitle = getTitle(
                            context, actualUri, false /*followSettingsUri*/, allowRemote);
                    title = context
                            .getString(com.android.internal.R.string.ringtone_default_with_actual,
                                    actualTitle);
                }
            } else {
                Cursor cursor = null;
                try {
                    if (MediaStore.AUTHORITY.equals(authority)) {
                        final String mediaSelection = allowRemote ? null : MEDIA_SELECTION;
                        cursor = res.query(uri, MEDIA_COLUMNS, mediaSelection, null, null);
                        if (cursor != null && cursor.getCount() == 1) {
                            cursor.moveToFirst();
                            return cursor.getString(1);
                        }
                        // missing cursor is handled below
                    }
                } catch (SecurityException e) {
                    IRingtonePlayer mRemotePlayer = null;
                    if (allowRemote) {
                        AudioManager audioManager =
                                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                        mRemotePlayer = audioManager.getRingtonePlayer();
                    }
                    if (mRemotePlayer != null) {
                        try {
                            title = mRemotePlayer.getTitle(uri);
                        } catch (RemoteException re) {
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = null;
                }
                if (title == null) {
                    title = uri.getLastPathSegment();
                }
            }
        } else {
            title = context.getString(com.android.internal.R.string.ringtone_silent);
        }

        if (title == null) {
            title = context.getString(com.android.internal.R.string.ringtone_unknown);
            if (title == null) {
                title = "";
            }
        }

        return title;
    }

    /**
     * Set {@link Uri} to be used for ringtone playback. Attempts to open
     * locally, otherwise will delegate playback to remote
     * {@link IRingtonePlayer}.
     *
     * @hide
     */
    @UnsupportedAppUsage
    public void setUri(Uri uri) {
        setUri(uri, null);
    }

    /**
     * Set {@link Uri} to be used for ringtone playback. Attempts to open
     * locally, otherwise will delegate playback to remote
     * {@link IRingtonePlayer}. Add {@link VolumeShaper} if required.
     *
     * @hide
     */
    public void setUri(Uri uri, @Nullable VolumeShaper.Configuration volumeShaperConfig) {
        mVolumeShaperConfig = volumeShaperConfig;
        destroyLocalPlayer();

        mUri = uri;
        if (mUri == null) {
            return;
        }

        // TODO: detect READ_EXTERNAL and specific content provider case, instead of relying on throwing

        // try opening uri locally before delegating to remote player
        mLocalPlayer = new MediaPlayer();
        try {
            mLocalPlayer.setDataSource(mContext, mUri);
            setCustomAudioAttributes();
            mLocalPlayer.setAudioAttributes(mFinalAudioAttributes);
            synchronized (mPlaybackSettingsLock) {
                applyPlaybackProperties_sync();
            }
            if (mVolumeShaperConfig != null) {
                mVolumeShaper = mLocalPlayer.createVolumeShaper(mVolumeShaperConfig);
            }
            mLocalPlayer.prepare();

        } catch (SecurityException | IOException e) {
            destroyLocalPlayer();
            if (!mAllowRemote) {
                Log.w(TAG, "Remote playback not allowed: " + e);
            }
        }

        if (LOGD) {
            if (mLocalPlayer != null) {
                Log.d(TAG, "Successfully created local player");
            } else {
                Log.d(TAG, "Problem opening; delegating to remote player");
            }
        }
    }

    /** {@hide} */
    @UnsupportedAppUsage
    public Uri getUri() {
        return mUri;
    }

    /**
     * Plays the ringtone.
     */
    public void play() {
        if (mLocalPlayer != null) {
            // do not play ringtones if stream volume is 0
            // (typically because ringer mode is silent).
            if (mAudioManager.getStreamVolume(
                    AudioAttributes.toLegacyStreamType(mAudioAttributes)) != 0) {
                startLocalPlayer();
            }
        } else if (mAllowRemote && (mRemotePlayer != null) && (mUri != null)) {
            final Uri canonicalUri = mUri.getCanonicalUri();
            final boolean looping;
            final float volume;
            synchronized (mPlaybackSettingsLock) {
                looping = mIsLooping;
                volume = mVolume;
            }
            try {
                mRemotePlayer.playWithVolumeShaping(mRemoteToken, canonicalUri, mAudioAttributes,
                        volume, looping, mVolumeShaperConfig);
            } catch (RemoteException e) {
                if (!playFallbackRingtone()) {
                    Log.w(TAG, "Problem playing ringtone: " + e);
                }
            }
        } else {
            if (!playFallbackRingtone()) {
                Log.w(TAG, "Neither local nor remote playback available");
            }
        }
    }

    /**
     * Stops a playing ringtone.
     */
    public void stop() {
        if (mLocalPlayer != null) {
            destroyLocalPlayer();
        } else if (mAllowRemote && (mRemotePlayer != null)) {
            try {
                mRemotePlayer.stop(mRemoteToken);
            } catch (RemoteException e) {
                Log.w(TAG, "Problem stopping ringtone: " + e);
            }
        }
    }

    private void destroyLocalPlayer() {
        if (mLocalPlayer != null) {
            mLocalPlayer.setOnCompletionListener(null);
            mLocalPlayer.reset();
            mLocalPlayer.release();
            mLocalPlayer = null;
            mVolumeShaper = null;
            synchronized (sActiveRingtones) {
                sActiveRingtones.remove(this);
            }
        }
    }

    private void startLocalPlayer() {
        if (mLocalPlayer == null) {
            return;
        }
        synchronized (sActiveRingtones) {
            sActiveRingtones.add(this);
        }
        mLocalPlayer.setOnCompletionListener(mCompletionListener);
        mLocalPlayer.start();
        if (mVolumeShaper != null) {
            mVolumeShaper.apply(VolumeShaper.Operation.PLAY);
        }
    }

    /**
     * Whether this ringtone is currently playing.
     *
     * @return True if playing, false otherwise.
     */
    public boolean isPlaying() {
        if (mLocalPlayer != null) {
            return mLocalPlayer.isPlaying();
        } else if (mAllowRemote && (mRemotePlayer != null)) {
            try {
                return mRemotePlayer.isPlaying(mRemoteToken);
            } catch (RemoteException e) {
                Log.w(TAG, "Problem checking ringtone: " + e);
                return false;
            }
        } else {
            Log.w(TAG, "Neither local nor remote playback available");
            return false;
        }
    }

    private boolean playFallbackRingtone() {
        if (mAudioManager.getStreamVolume(AudioAttributes.toLegacyStreamType(mAudioAttributes))
                != 0) {
            int ringtoneType = RingtoneManager.getDefaultType(mUri);
            if (ringtoneType == -1 ||
                    RingtoneManager.getActualDefaultRingtoneUri(mContext, ringtoneType) != null) {
                // Default ringtone, try fallback ringtone.
                try {
                    AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(
                            com.android.internal.R.raw.fallbackring);
                    if (afd != null) {
                        mLocalPlayer = new MediaPlayer();
                        if (afd.getDeclaredLength() < 0) {
                            mLocalPlayer.setDataSource(afd.getFileDescriptor());
                        } else {
                            mLocalPlayer.setDataSource(afd.getFileDescriptor(),
                                    afd.getStartOffset(),
                                    afd.getDeclaredLength());
                        }
                        mLocalPlayer.setAudioAttributes(mAudioAttributes);
                        synchronized (mPlaybackSettingsLock) {
                            applyPlaybackProperties_sync();
                        }
                        if (mVolumeShaperConfig != null) {
                            mVolumeShaper = mLocalPlayer.createVolumeShaper(mVolumeShaperConfig);
                        }
                        mLocalPlayer.prepare();
                        startLocalPlayer();
                        afd.close();
                        return true;
                    } else {
                        Log.e(TAG, "Could not load fallback ringtone");
                    }
                } catch (IOException ioe) {
                    destroyLocalPlayer();
                    Log.e(TAG, "Failed to open fallback ringtone");
                } catch (NotFoundException nfe) {
                    Log.e(TAG, "Fallback ringtone does not exist");
                }
            } else {
                Log.w(TAG, "not playing fallback for " + mUri);
            }
        }
        return false;
    }

    void setTitle(String title) {
        mTitle = title;
    }

    @Override
    protected void finalize() {
        if (mLocalPlayer != null) {
            mLocalPlayer.release();
        }
    }

    class MyOnCompletionListener implements MediaPlayer.OnCompletionListener {
        @Override
        public void onCompletion(MediaPlayer mp) {
            synchronized (sActiveRingtones) {
                sActiveRingtones.remove(Ringtone.this);
            }
            mp.setOnCompletionListener(null); // Help the Java GC: break the refcount cycle.
        }
    }
}
