package io.github.erica1018.dandancinema;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.Locale;

/** TV transport controls. Preview seeking never changes playback until confirmed. */
final class PlayerControls {
    private final Activity activity;
    private final VideoView video;
    private final Handler clock;
    private final FrameLayout overlay;
    private final TextView state, position, duration, help;
    private final SeekBar timeline;
    private boolean ready, closed, seeking, resumeAfterSeek, buffering, pendingSeek;
    private boolean resumeOnSeekComplete;
    private boolean live;
    private MediaPlayer media;
    private int target;
    private final Runnable hideLater=new Runnable(){public void run(){
        if(closed)return;
        if(!seeking&&!buffering&&!pendingSeek&&video.isPlaying())hide();
    }};

    PlayerControls(Activity owner,VideoView player,Handler handler,String title,FrameLayout frame){
        activity=owner;video=player;clock=handler;
        overlay=new FrameLayout(activity);
        frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        TextView heading=label(title,22);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(dp(32),dp(16),dp(32),dp(16));heading.setBackgroundColor(0xB3000000);
        overlay.addView(heading,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32),dp(12),dp(32),dp(20));panel.setBackgroundColor(0xD9000000);
        LinearLayout times=new LinearLayout(activity);times.setGravity(Gravity.CENTER_VERTICAL);
        position=label("00:00",20);position.setMinWidth(dp(100));
        state=label(I18n.t("正在准备播放…","Preparing playback…"),19);state.setGravity(Gravity.CENTER);
        duration=label("--:--",20);duration.setGravity(Gravity.RIGHT);duration.setMinWidth(dp(100));
        times.addView(position,new LinearLayout.LayoutParams(-2,-2));
        times.addView(state,new LinearLayout.LayoutParams(0,-2,1));
        times.addView(duration,new LinearLayout.LayoutParams(-2,-2));panel.addView(times);
        timeline=new SeekBar(activity);timeline.setFocusable(false);
        int gold=Color.rgb(229,235,143);
        timeline.setProgressTintList(ColorStateList.valueOf(gold));
        timeline.setThumbTintList(ColorStateList.valueOf(gold));
        timeline.setSecondaryProgressTintList(ColorStateList.valueOf(Color.rgb(120,130,120)));
        timeline.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(62,68,62)));
        timeline.setContentDescription(I18n.t("影片进度","Playback progress"));
        panel.addView(timeline,new LinearLayout.LayoutParams(-1,dp(34)));
        help=label("",16);help.setGravity(Gravity.CENTER);help.setTextColor(0xFFCAD2C9);
        panel.addView(help);overlay.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){beginSeek();}
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser&&ready){if(!seeking)beginSeek();target=clamp(value);update();}}
            public void onStopTrackingTouch(SeekBar bar){if(seeking)commitSeek();}
        });
        show();
    }

    private int dp(int value){return (int)(activity.getResources().getDisplayMetrics().density*value+.5f);}
    private TextView label(String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(0xFFF2F4E9);return view;}
    private String time(int ms){int total=Math.max(0,ms)/1000,h=total/3600,m=(total/60)%60,s=total%60;return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%02d:%02d",m,s);}
    private int clamp(int value){return Math.max(0,Math.min(value,Math.max(0,video.getDuration()-1000)));}
    private void setLabel(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}

    void prepared(MediaPlayer player){media=player;ready=true;Log.i("Watchroom","controller_ready duration_ms="+video.getDuration());show();}
    void live(boolean value){live=value;timeline.setVisibility(live?View.GONE:View.VISIBLE);}
    void buffering(boolean value){if(!ready||closed)return;buffering=value;show();}
    void seekComplete(){if(closed)return;boolean resume=pendingSeek&&resumeOnSeekComplete;pendingSeek=false;resumeOnSeekComplete=false;if(resume)video.start();Log.i("Watchroom","seek_complete_ms="+video.getCurrentPosition());show();}

    void update(){
        if(closed)return;
        int length=Math.max(0,video.getDuration()),at=seeking?target:Math.max(0,video.getCurrentPosition());
        if(timeline.isEnabled()!=(ready&&length>0))timeline.setEnabled(ready&&length>0);
        if(timeline.getMax()!=Math.max(1,length))timeline.setMax(Math.max(1,length));
        if(timeline.getProgress()!=at)timeline.setProgress(at);
        int buffered=(int)((long)length*video.getBufferPercentage()/100);
        if(timeline.getSecondaryProgress()!=buffered)timeline.setSecondaryProgress(buffered);
        setLabel(position,live?I18n.t("在线画面","Online video"):time(at));setLabel(duration,live?I18n.t("实时中转","Live relay"):length>0?time(length):"--:--");
        setLabel(state,!ready?I18n.t("正在准备播放…","Preparing playback…"):seeking?I18n.t("选择位置  ·  ","Seek to · ")+time(target):pendingSeek?I18n.t("正在跳转…","Seeking…"):buffering?I18n.t("正在缓冲…","Buffering…"):video.isPlaying()?I18n.t("正在播放","Playing"):I18n.t("已暂停","Paused"));
        setLabel(help,seeking?I18n.t("左右 选择位置（长按加速）    确认 跳转    返回 取消","Left/Right seek (hold to speed up) · OK confirm · Back cancel"):I18n.t("确认 暂停/继续    左右 选择进度（长按加速）    上下 显示进度    返回 影片页","OK pause/play · Left/Right seek · Up/Down controls · Back details"));
        if(live)setLabel(help,I18n.t("确认 暂停画面/继续    上下 显示提示    返回 结束在线播放；电脑网页仍按原进度播放","OK pause/play · Up/Down controls · Back details"));
    }

    private void show(){
        if(closed)return;
        if(overlay.getVisibility()!=View.VISIBLE){overlay.setVisibility(View.VISIBLE);Log.i("Watchroom","controls_visible=true");}
        update();clock.removeCallbacks(hideLater);
        if(ready&&!seeking&&!buffering&&!pendingSeek&&video.isPlaying())clock.postDelayed(hideLater,4500);
    }
    private void hide(){if(closed)return;overlay.setVisibility(View.GONE);Log.i("Watchroom","controls_visible=false");}

    private void beginSeek(){
        if(live||!ready||seeking||pendingSeek)return;
        resumeAfterSeek=video.isPlaying();target=clamp(video.getCurrentPosition());seeking=true;video.pause();show();
    }
    private void moveSeek(int direction,KeyEvent event){
        if(live){show();return;}if(!ready||pendingSeek)return;
        beginSeek();long held=event.getEventTime()-event.getDownTime();
        int step=held>=2000||event.getRepeatCount()>=10?60000:held>=800||event.getRepeatCount()>0?30000:10000;
        target=(int)Math.max(0,Math.min((long)target+direction*(long)step,Math.max(0,video.getDuration()-1000)));
        Log.i("Watchroom","seek_preview_ms="+target+" step_ms="+step);show();
    }
    private void commitSeek(){
        if(!seeking)return;
        int destination=target;boolean resume=resumeAfterSeek;seeking=false;pendingSeek=true;resumeOnSeekComplete=resume;video.pause();
        if(Build.VERSION.SDK_INT>=26)media.seekTo((long)destination,MediaPlayer.SEEK_CLOSEST);else video.seekTo(destination);
        Log.i("Watchroom","seek_commit_ms="+destination+" resume="+resume);show();
    }
    boolean cancelSeek(){
        if(!seeking)return false;
        seeking=false;if(resumeAfterSeek)video.start();
        Log.i("Watchroom","seek_cancel_ms="+video.getCurrentPosition());show();return true;
    }
    private void toggle(){if(!ready)return;if(pendingSeek){resumeOnSeekComplete=!resumeOnSeekComplete;show();return;}if(seeking){commitSeek();return;}if(video.isPlaying())video.pause();else video.start();show();}

    boolean key(KeyEvent event){
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_MEDIA_REWIND||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){
            if(down)moveSeek(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_MEDIA_REWIND?-1:1,event);return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(down&&event.getRepeatCount()==0)toggle();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){if(down)show();return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE){
            if(down&&ready){if(seeking)cancelSeek();if(key==KeyEvent.KEYCODE_MEDIA_PLAY)video.start();else video.pause();show();}return true;
        }
        return false;
    }
    void hostPaused(){seeking=false;resumeAfterSeek=false;resumeOnSeekComplete=false;clock.removeCallbacks(hideLater);if(!closed){overlay.setVisibility(View.VISIBLE);update();}}
    void close(){closed=true;clock.removeCallbacks(hideLater);overlay.setVisibility(View.GONE);}
}
