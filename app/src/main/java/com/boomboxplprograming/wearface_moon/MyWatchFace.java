package com.boomboxplprograming.wearface_moon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;

import android.support.wearable.watchface.CanvasWatchFaceService;
import android.text.TextPaint;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
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
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    static final double MOON_LIFE = 29.53;

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
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        Calendar calendar;
        Calendar superMoonDate;
        SimpleDateFormat hourFormat;
        SimpleDateFormat dateFormat;
        Date lastRedrawDate;

        Bitmap backgroundBitmap;
        Bitmap backgroundScaledBitmap;
        Bitmap eclipseBitmap;
        Paint blackPaint;
        Paint backgroundPaint;
        Paint eclipsePaint;
        PorterDuffXfermode ADD;
        PorterDuffXfermode SRC_OUT;

        TextPaint lightTextPaint;
        TextPaint darkTextPaint;
        TextPaint ambientTextPaint;
        int BIG_TEXT_SIZE;
        int SMALL_TEXT_SIZE;
        String batteryLevel;
        String dateDescription;
        String timeDescription;

        //Handling one per draw allocation
        Rect textBounds;

        // handler to update the time once a second in interactive mode
        final Handler updateTimeHandler = new UpdateTimeHandler(new WeakReference<>(this));

        // receiver to update the time zone
        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        final BroadcastReceiver batteryPercentageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                float batteryPct =level * 100 / (float)scale;
                batteryLevel = Integer.toString((int)batteryPct) + "%";

                invalidate();
            }
        };

        final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTime(new Date());
                dateDescription = GetTextFromDate(calendar.getTime());
                RedrawEclipse(calendar.getTime());
            }
        };

        @Override
        public void onCreate(SurfaceHolder sfHolder) {
            super.onCreate(sfHolder);

            //Load background
            Resources resources = getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.moonbackground, null);
            backgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            //Init eclipse bitmap
            Rect sfRect = sfHolder.getSurfaceFrame();
            eclipseBitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ALPHA_8);

            //Set paint
            backgroundPaint = new Paint();
            backgroundPaint.setARGB(255, 255, 255, 255);
            backgroundPaint.setStrokeWidth(5.0f);
            backgroundPaint.setAntiAlias(true);
            backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            backgroundPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));

            blackPaint = new Paint();
            blackPaint.setARGB(255, 0, 0, 0);
            blackPaint.setStrokeWidth(5.0f);
            blackPaint.setAntiAlias(true);
            blackPaint.setStrokeCap(Paint.Cap.ROUND);


            eclipsePaint = new Paint();
            eclipsePaint.setARGB(200, 0, 0, 0);
            eclipsePaint.setStrokeWidth(5.0f);
            eclipsePaint.setAntiAlias(true);
            eclipsePaint.setStrokeCap(Paint.Cap.ROUND);


            ADD = new PorterDuffXfermode(PorterDuff.Mode.ADD);
            SRC_OUT = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);

            lightTextPaint = new TextPaint();
            lightTextPaint.setARGB(255, 255, 255, 255);
            lightTextPaint.setStrokeWidth(5.0f);
            lightTextPaint.setAntiAlias(true);
            lightTextPaint.setStrokeCap(Paint.Cap.ROUND);
            lightTextPaint.setTextSize(70);
            lightTextPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

            darkTextPaint = new TextPaint(lightTextPaint);
            darkTextPaint.setARGB(255, 0, 0, 0);
            darkTextPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));

            ambientTextPaint = new TextPaint(lightTextPaint);
            ambientTextPaint.setARGB(180, 255, 255, 255);
            ambientTextPaint.setXfermode(null);
            ambientTextPaint.setAntiAlias(false);

            calendar = Calendar.getInstance();
            hourFormat = new SimpleDateFormat("HH:mm");
            dateFormat = new SimpleDateFormat("EE dd MMM");

            batteryLevel = "-%";
            dateDescription = GetTextFromDate(calendar.getTime());
            timeDescription = GetTextFromTime(calendar.getTime());


            //Last full moon
            superMoonDate = Calendar.getInstance();
            superMoonDate.set(2019,0,21);

            registerReceiver(dateChangeReceiver, new IntentFilter(Intent.ACTION_DATE_CHANGED));

            lastRedrawDate = null;
            textBounds = null;


        }

        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            invalidate();

            if (visible) {
                //Timezone
                IntentFilter filterZone = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                registerReceiver(timeZoneReceiver, filterZone);

                // Update time zone in case it changed while we weren't visible.
                calendar.setTimeZone(TimeZone.getDefault());
                //Battery
                IntentFilter filterBattery = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                registerReceiver(batteryPercentageReceiver, filterBattery);


            } else {
                //Timezone
                unregisterReceiver(timeZoneReceiver);
                //Battery
                unregisterReceiver(batteryPercentageReceiver);

            }

            // Whether the timer should be running depends on whether we're visible and
            // whether we're in ambient mode, so we may need to start or stop the timer
            updateTimer();
        }



        @Override
        public void onTimeTick() {
            super.onTimeTick();

            calendar.setTime(new Date());
            timeDescription = GetTextFromTime(calendar.getTime());

            invalidate();
        }


        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder, int format, int width, int height) {
            if (backgroundScaledBitmap == null
                    || backgroundScaledBitmap.getWidth() != width
                    || backgroundScaledBitmap.getHeight() != height) {
                backgroundScaledBitmap = Bitmap.createScaledBitmap(backgroundBitmap,
                        width, height, true /* filter */);
            }
            eclipseBitmap = Bitmap.createScaledBitmap(eclipseBitmap, width, height, true);
            BIG_TEXT_SIZE = height / 4;
            SMALL_TEXT_SIZE = height / 10;
            super.onSurfaceChanged(holder, format, width, height);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            Date currentDate = calendar.getTime();

            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            if(!isInAmbientMode()) {

                //Eclipse
                canvas.drawBitmap(GetEclipse(currentDate), 0, 0, eclipsePaint);
                //Time
                DrawCenteredText(timeDescription, lightTextPaint, darkTextPaint, canvas, bounds, 0, 0, BIG_TEXT_SIZE);

                float yNbdOffset = darkTextPaint.ascent();
                //Date
                DrawCenteredText(dateDescription, lightTextPaint, darkTextPaint, canvas, bounds, 0, -yNbdOffset, SMALL_TEXT_SIZE);
                //Battery
                DrawCenteredText(batteryLevel, lightTextPaint, darkTextPaint, canvas, bounds, 0, yNbdOffset, SMALL_TEXT_SIZE);//+ 2*darkTextPaint.descent());
                //Background
                canvas.drawBitmap(backgroundScaledBitmap, 0, 0, backgroundPaint);
            }
            else{
                //Ambient
                DrawCenteredText(timeDescription, ambientTextPaint, null, canvas, bounds, 0,0, BIG_TEXT_SIZE);
            }

        }

        private void DrawNegativeText(String stringToShow, TextPaint normalPaint, TextPaint negativePaint,Canvas canvas, float posX, float posY){

            canvas.drawText(stringToShow, posX, posY, normalPaint);

            if(negativePaint != null) {
                canvas.drawText(stringToShow, posX, posY, negativePaint);
            }
        }

        private void DrawCenteredText(String stringToShow, TextPaint textPaint, TextPaint textNegativePaint,Canvas canvas, Rect bounds,float xOffset, float yOffset, float fontSize){
            if(textBounds == null){
                textBounds = new Rect();
            }
            textPaint.setTextSize(fontSize);
            if(textNegativePaint != null) {
                textNegativePaint.setTextSize(fontSize);
            }
            textPaint.getTextBounds(stringToShow, 0, stringToShow.length(), textBounds);
            DrawNegativeText(stringToShow, textPaint, textNegativePaint, canvas,
                    bounds.centerX() - textBounds.centerX() - xOffset,
                    bounds.centerY() - textBounds.centerY() - textPaint.descent() - yOffset);

        }

        private String GetTextFromTime(Date time){
            return hourFormat.format(time);
        }

        private String GetTextFromDate(Date time){
            return dateFormat.format(time);
        }

        private void RedrawEclipse(Date currentDate){
            Canvas eclipseCanvas = new Canvas(eclipseBitmap);
            Rect bounds = eclipseCanvas.getClipBounds();

            double moonPhase = GetMoonPhase(currentDate);
            float width = (float)((bounds.centerX()+5) * Math.sqrt(Math.abs((0.25 - Math.abs(moonPhase))/0.25)));

            blackPaint.setXfermode(ADD);
            if(moonPhase < 0){
                eclipseCanvas.drawArc(bounds.left,bounds.top, bounds.right, bounds.bottom, 270 ,180, false, blackPaint);
            }
            else{
                eclipseCanvas.drawArc(bounds.left,bounds.top, bounds.right, bounds.bottom, 90 ,180, false, blackPaint);
            }
            if(Math.abs(moonPhase)> 0.25) {
                blackPaint.setXfermode(SRC_OUT);
            }

            eclipseCanvas.drawOval(bounds.centerX() - width, bounds.top, bounds.centerX() + width, bounds.bottom, blackPaint);

            lastRedrawDate = currentDate;

        }

        //Return moon phase in (-0.5,0.5) where 0 is new moon
        private double GetMoonPhase(Date currentDate){
           return ChronoUnit.DAYS.between(superMoonDate.getTime().toInstant(), currentDate.toInstant()) / MOON_LIFE % 1 - 0.5;
        }

        private Bitmap GetEclipse(Date currentDate){

            if(lastRedrawDate == null || lastRedrawDate.getDay() != currentDate.getDay()){
                RedrawEclipse(currentDate);
            }
            return eclipseBitmap;

        }

    }

    private static class UpdateTimeHandler extends Handler {
        private WeakReference<Engine> engineReference;

        UpdateTimeHandler(WeakReference<Engine> engine) {
            this.engineReference = engine;
        }

        @Override
        public void handleMessage(Message message) {
            Engine engine = engineReference.get();
            if (engine != null) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        engine.invalidate();
                        if (engine.shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        }
    }


}
