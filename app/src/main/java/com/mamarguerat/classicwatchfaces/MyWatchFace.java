package com.mamarguerat.classicwatchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(133);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 2f;
        private static final float MINUTE_STROKE_WIDTH = 2f;
        private static final float SECOND_TICK_STROKE_WIDTH = 1f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 3;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mBackgroundPaint;
        private TextPaint datePaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(false)
                    .build());

            mCalendar = Calendar.getInstance();


            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            mGrayBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.watchface1_alwayson);
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.WHITE;
            mWatchHandShadowColor = Color.argb(0xff, 0x80, 0x80, 0x80);

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStrokeCap(Paint.Cap.ROUND);

            datePaint = new TextPaint();
            datePaint.setColor(Color.BLACK);
            datePaint.setAntiAlias(true);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(Typeface.SANS_SERIF);
            datePaint.setTextSize(25);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(Color.BLACK);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

                mHourPaint.setStyle(Paint.Style.STROKE);
                mMinutePaint.setStyle(Paint.Style.STROKE);
                mTickAndCirclePaint.setStyle(Paint.Style.FILL);

                mHourPaint.setStrokeWidth(1f);
                mMinutePaint.setStrokeWidth(1f);
                mTickAndCirclePaint.setStrokeWidth(1f);

                datePaint.setColor(Color.WHITE);
                datePaint.setAntiAlias(false);

            } else {
                mWatchHandColor = Color.WHITE;
                mWatchHandHighlightColor = Color.WHITE;
                mWatchHandShadowColor = Color.argb(0xff, 0x99, 0x99, 0x99);

                mHourPaint = new Paint();
                mHourPaint.setColor(mWatchHandColor);
                mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
                mHourPaint.setAntiAlias(true);
                mHourPaint.setStrokeCap(Paint.Cap.ROUND);
                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                mMinutePaint = new Paint();
                mMinutePaint.setColor(mWatchHandColor);
                mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
                mMinutePaint.setAntiAlias(true);
                mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                mSecondPaint = new Paint();
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
                mSecondPaint.setAntiAlias(true);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                mTickAndCirclePaint = new Paint();
                mTickAndCirclePaint.setColor(mWatchHandShadowColor);
                mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
                mTickAndCirclePaint.setAntiAlias(true);

                mHourPaint.setStyle(Paint.Style.FILL);
                mMinutePaint.setStyle(Paint.Style.FILL);

                datePaint.setColor(Color.BLACK);
                datePaint.setAntiAlias(true);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            sHourHandLength = 0.65f * width / 2;
            sMinuteHandLength = 0.85f * width / 2;
            mSecondHandLength = 0.85f * width / 2;

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            //if (!mBurnInProtection && !mLowBitAmbient) {
                //scale = ((float) width) / (float) mGrayBackgroundBitmap.getWidth();

                mGrayBackgroundBitmap = Bitmap.createScaledBitmap(mGrayBackgroundBitmap,
                        (int) (mGrayBackgroundBitmap.getWidth() * scale),
                        (int) (mGrayBackgroundBitmap.getHeight() * scale), true);
                Canvas canvas = new Canvas(mGrayBackgroundBitmap);
                Paint grayPaint = new Paint();
                ColorMatrix colorMatrix = new ColorMatrix();
                colorMatrix.setSaturation(0);
                ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
                grayPaint.setColorFilter(filter);
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, grayPaint);
            //}
        }


        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {

            /*if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else*/ if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }
        }

        private void drawWatchFace(Canvas canvas) {

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            /*float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }*/


            final int today = mCalendar.get((Calendar.DAY_OF_MONTH));


            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f + seconds / 10f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            //Show date
            canvas.drawText(String.valueOf(today), mCenterX*2*0.829f, mCenterY+8, datePaint);

            //Show hour Hand
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            if(mAmbient)
                drawHand(canvas, sHourHandLength, mTickAndCirclePaint);
            else
                canvas.drawCircle(mCenterX, mCenterY, mCenterX/15, mHourPaint);
            drawHand(canvas, sHourHandLength, mHourPaint);
            //Show minute Hand
            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            if(mAmbient)
                drawHand(canvas, sMinuteHandLength, mTickAndCirclePaint);
            else
                canvas.drawCircle(mCenterX, mCenterY, mCenterX/20, mMinutePaint);
            drawHand(canvas, sMinuteHandLength, mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                //Show seconds Hand
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX, mCenterY + mSecondHandLength/3.5f, mCenterX, mCenterY - mSecondHandLength, mSecondPaint);
                canvas.drawCircle(mCenterX, mCenterY, mCenterX/25, mSecondPaint);

                //Small fixation round
                canvas.drawCircle(mCenterX, mCenterY, mCenterX/80, mTickAndCirclePaint);
            }
            else
            {
                canvas.drawCircle(mCenterX, mCenterY, mCenterX/15, mTickAndCirclePaint);
                canvas.drawCircle(mCenterX, mCenterY, mCenterX/15, mHourPaint);
            }

            /* Restore the canvas' original orientation. */
            canvas.restore();
        }

        private void drawHand(Canvas canvas, float handLength, Paint handPaint)
        {
            Path path = new Path();
            path.moveTo(mCenterX - 800f/handLength, mCenterY - handLength);
            path.lineTo(mCenterX + 800f/handLength, mCenterY - handLength);
            path.lineTo(mCenterX + 1500f/handLength, mCenterY);
            path.lineTo(mCenterX - 1500f/handLength, mCenterY);
            path.lineTo(mCenterX - 800f/handLength, mCenterY - handLength);
            path.close();
            canvas.drawPath(path, handPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
