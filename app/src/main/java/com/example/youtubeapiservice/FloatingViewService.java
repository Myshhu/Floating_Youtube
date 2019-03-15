package com.example.youtubeapiservice;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;


public class FloatingViewService extends Service {

    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private WindowManager mWindowManager;
    private YouTubePlayer youTubePlayer;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotification();
        createFloatingView();
        createLayoutParams();
        createWindowManager();
        addFloatingViewToWindowManager();

        getYouTubePlayer();

        setViewItemsListeners();
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createOwnNotificationChannel();
        } else {
            startForeground(1, new Notification());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createOwnNotificationChannel() {
        String NOTIFICATION_CHANNEL_ID = "com.example.youtubeapiservice";
        String channelName = "YoutubeService";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.RED);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Youtube service is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    private void createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_item, null);
    }

    private void createLayoutParams() {
        int layoutFlag = createLayoutFlag();

        WindowManager.LayoutParams createdLayoutParams;
        //Create view params
        createdLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, 0,
                PixelFormat.TRANSLUCENT
        );
        createdLayoutParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        //Specify the view position
        //Initially view will be added to top-left corner
        createdLayoutParams.gravity = Gravity.TOP | Gravity.START;
        createdLayoutParams.x = 0;
        createdLayoutParams.y = 100;
        this.layoutParams = createdLayoutParams;
    }

    private int createLayoutFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private void createWindowManager() {
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    private void addFloatingViewToWindowManager() {
        mWindowManager.addView(floatingView, layoutParams);
    }

    private void getYouTubePlayer() {
        YouTubePlayerView youTubePlayerView = floatingView.findViewById(R.id.ytPlayerView);
        youTubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NotNull YouTubePlayer player) {
                youTubePlayer = player;
            }
        });
    }

    private void setViewItemsListeners() {
        setFloatingViewOnTouchListener();
        setSearchBarOnClickListener();
        setButtonSearchOnClickListener();
        setButtonResizeOnTouchListener();
        setButtonMoveOnTouchListener();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setFloatingViewOnTouchListener() {
        //Clicking outside widget invokes floatingView touch event
        //that is used for getting focus off widget to allow using phone
        floatingView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                onFloatingViewDownTouch();
            }
            return false;
        });
    }

    private void onFloatingViewDownTouch() {
        EditText etSearch = floatingView.findViewById(R.id.etSearch);
        if (etSearch.hasFocus()) {
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            layoutParams.flags = 0;
        }
        mWindowManager.updateViewLayout(floatingView, layoutParams);
    }

    private void setSearchBarOnClickListener() {
        EditText etSearch = floatingView.findViewById(R.id.etSearch);
        etSearch.setOnClickListener(v -> {
            //Make widget focusable by clearing flags
            layoutParams.flags = 0;
            mWindowManager.updateViewLayout(floatingView, layoutParams);
        });
    }

    private void setButtonSearchOnClickListener() {
        ImageButton imgBtnSearch = floatingView.findViewById(R.id.imgBtnSearch);
        imgBtnSearch.setOnClickListener(v -> {
            EditText etSearch = floatingView.findViewById(R.id.etSearch);
            etSearch.clearFocus();
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mWindowManager.updateViewLayout(floatingView, layoutParams);

            String searchQuery = etSearch.getText().toString();
            performVideoSearchAndPlay(searchQuery);
        });
    }

    private void performVideoSearchAndPlay(String searchQuery) {
        new Thread (() -> {
            try {
                JSONObject videoInformationObject = new VideosInformation(searchQuery, getApplicationContext()).getFirstVideoInfo();
                String videoId = "";
                if (videoInformationObject != null) {
                    videoId = videoInformationObject.getJSONObject("id").getString("videoId");
                }
                youTubePlayer.loadVideo(videoId, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setButtonResizeOnTouchListener() {
        ImageButton imgBtnResize = floatingView.findViewById(R.id.imgBtnResize);

        imgBtnResize.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX = 0.0f;
            private float initialTouchY = 0.0f;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int xDiff = (int) (event.getRawX() - initialTouchX);
                        int yDiff = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(xDiff) > 9 || Math.abs(yDiff) > 9) {
                            layoutParams.width = floatingView.getWidth() + xDiff;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            mWindowManager.updateViewLayout(floatingView, layoutParams);
                            break;
                        }
                }
                return false;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setButtonMoveOnTouchListener() {
        Button btnMove = floatingView.findViewById(R.id.btnMove);

        btnMove.setOnTouchListener(new View.OnTouchListener() {
            int initialX = 0;
            int initialY = 0;
            float initialTouchX = 0f;
            float initialTouchY = 0f;
            float timeAfterTouch = 0f;
            float startTime = 0f;
            boolean widgetMoved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        widgetMoved = false;

                        //Remember the initial position
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;

                        startTime = System.currentTimeMillis();

                        //Get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int xDiff = (int) (event.getRawX() - initialTouchX);
                        int yDiff = (int) (event.getRawY() - initialTouchY);

                        layoutParams.x = initialX + xDiff;
                        layoutParams.y = initialY + yDiff;
                        mWindowManager.updateViewLayout(floatingView, layoutParams);

                        if (xDiff > 10 || yDiff > 10) {
                            widgetMoved = true;
                        }
                }
                timeAfterTouch = System.currentTimeMillis() - startTime;
                if (timeAfterTouch > 1000 && !widgetMoved) {
                    stopSelf();
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        if (floatingView != null) mWindowManager.removeView(floatingView);
    }
}