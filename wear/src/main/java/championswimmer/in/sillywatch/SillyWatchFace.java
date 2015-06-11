/*
 * Copyright (C) 2014 The Android Open Source Project
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

package championswimmer.in.sillywatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SillyWatchFace extends CanvasWatchFaceService {
    private static Typeface NORMAL_TYPEFACE;


    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        NORMAL_TYPEFACE = Typeface.createFromAsset(getAssets(), "YanoneKaffeesatz-Thin.ttf");
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;

        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset1;
        float mYOffset2;
        float mYOffset3;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            NORMAL_TYPEFACE = Typeface.createFromAsset(getAssets(), "YanoneKaffeesatz-Thin.ttf");

            setWatchFaceStyle(new WatchFaceStyle.Builder(SillyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SillyWatchFace.this.getResources();
            mYOffset1 = resources.getDimension(R.dimen.digital_y1_offset);
            mYOffset2 = resources.getDimension(R.dimen.digital_y2_offset);
            mYOffset3 = resources.getDimension(R.dimen.digital_y3_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SillyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SillyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SillyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private String getUnitsWord(int n) {
            String hrTxt = "";
            switch (n) {
                case 1: hrTxt = "bun"; break;
                case 2: hrTxt = "too"; break;
                case 3: hrTxt = "tree"; break;
                case 4: hrTxt = "foe"; break;
                case 5: hrTxt = "phi"; break;
                case 6: hrTxt = "see"; break;
                case 7: hrTxt = "swan"; break;
                case 8: hrTxt = "ate"; break;
                case 9: hrTxt = "nay"; break;
            }
            return hrTxt;
        }

        private String getTensWord(int n) {
            if (n>=20 && n<30) return "venti";
            if (n>=30 && n<40) return "tatti";
            if (n>=40 && n<50) return  "potty";
            if (n>=50 && n<60) return "pity";

            return "";
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String hrTxt = "", minTxt = "";
            switch (mTime.hour) {
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9: hrTxt = getUnitsWord(mTime.hour); break;
                case 10: hrTxt = "tin"; break;
                case 11: hrTxt = "leven"; break;
                case 12: hrTxt = "twill"; break;
            }
            switch (mTime.minute) {
                case 10: minTxt = "tin"; break;
                case 11: minTxt = "leven"; break;
                case 12: minTxt = "twill"; break;
                case 13: minTxt = "tuttin"; break;
                case 14: minTxt = "putin"; break;
                case 15: minTxt = "pippn"; break;
                case 16: minTxt = "sickin"; break;
                case 17: minTxt = "swayin"; break;
                case 18: minTxt = "eatin"; break;
                case 19: minTxt = "neinein"; break;
                default: minTxt = getTensWord(mTime.minute) + getUnitsWord(mTime.minute%10);
            }
            String dayTxt = "";
            switch (mTime.weekDay) {
                case 1: dayTxt = "mon"; break;
                case 2: dayTxt = "tue"; break;
                case 3: dayTxt = "wed"; break;
                case 4: dayTxt = "thu"; break;
                case 5: dayTxt = "fri"; break;
                case 6: dayTxt = "sat"; break;
                case 7: dayTxt = "sun"; break;
            }
            String monthTxt = "";
            switch (mTime.month) {
                case 1: monthTxt = "jan"; break;
                case 2: monthTxt = "feb"; break;
                case 3: monthTxt = "mar"; break;
                case 4: monthTxt = "apr"; break;
                case 5: monthTxt = "may"; break;
                case 6: monthTxt = "jun"; break;
                case 7: monthTxt = "jul"; break;
                case 8: monthTxt = "aug"; break;
                case 9: monthTxt = "sep"; break;
                case 10: monthTxt = "oct"; break;
                case 11: monthTxt = "nov"; break;
                case 12: monthTxt = "dev"; break;
            }
            canvas.drawText(hrTxt, mXOffset, mYOffset1, mTextPaint);
            canvas.drawText(minTxt, mXOffset, mYOffset2, mTextPaint);
            canvas.drawText((dayTxt + monthTxt + mTime.monthDay), mXOffset, mYOffset3, mTextPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();

        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SillyWatchFace.Engine> mWeakReference;

        public EngineHandler(SillyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SillyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
