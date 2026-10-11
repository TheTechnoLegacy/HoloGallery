package com.techno.gallery;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

/** Plays a video (local or rtsp/http). */
public class VideoActivity extends Activity {
    private VideoView video;
    private int position;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        if (b != null) position = b.getInt("pos", 0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setContentView(root);

        MediaController mc = new MediaController(this);
        mc.setAnchorView(video);
        video.setMediaController(mc);
        video.setVideoURI(uri);
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                if (position > 0) video.seekTo(position);
                video.start();
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) { finish(); }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(VideoActivity.this, R.string.video_error, Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null) {
            position = video.getCurrentPosition();
            video.pause();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pos", position);
    }
}
