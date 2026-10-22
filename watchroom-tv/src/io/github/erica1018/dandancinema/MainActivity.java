package io.github.erica1018.dandancinema;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.HashMap;
import android.os.Handler;
import android.util.Log;

/** Native lean TV UI; no embedded browser, ads, analytics, or download manager. */
public class MainActivity extends Activity {
    private final int BACK=Color.rgb(18,26,23), PANEL=Color.rgb(38,53,46), GOLD=Color.rgb(229,235,143), WHITE=Color.rgb(242,244,233), MUTED=Color.rgb(174,189,176);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private JSONArray items=new JSONArray();
    private SharedPreferences prefs;
    private String server="", token="", filter="want", screen="home", selectedId="";
    private boolean loading=false;
    private boolean connected=true;
    private boolean managing=false;
    private HomeScreen homeView;
    private DetailScreen detailView;
    private ArtworkLoader artworkLoader;
    private VideoView video;
    private PlayerControls playerControls;
    private JSONObject detailItem;
    private int generation=0;
    private final Handler clock=new Handler();
    private boolean foreground=false;
    private int revision=-1;
    private TextView subtitleZh,subtitleEn;
    private JSONArray zh=new JSONArray(),en=new JSONArray();
    private int lastReported=-1;
    private LocalMediaProxy mediaProxy;
    private boolean onlineMirror;
    private boolean onlineVod;
    private String onlineStopUrl="";
    private final Runnable syncLoop=new Runnable(){public void run(){if(foreground&&!screen.equals("player"))refresh();clock.postDelayed(this,30000);}};
    private final Runnable playerLoop=new Runnable(){public void run(){
        if(video!=null&&screen.equals("player")){
            int t=video.getCurrentPosition();String zhText=cue(zh,t),enText=cue(en,t);if(!zhText.contentEquals(subtitleZh.getText()))subtitleZh.setText(zhText);if(!enText.contentEquals(subtitleEn.getText()))subtitleEn.setText(enText);
            if(playerControls!=null)playerControls.update();
            if(t/5000!=lastReported){lastReported=t/5000;savePosition();Log.i("Watchroom","playback_ms="+t+" playing="+video.isPlaying()+" zh="+!subtitleZh.getText().toString().isEmpty()+" en="+!subtitleEn.getText().toString().isEmpty());}
        }clock.postDelayed(this,200);
    }};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs=getSharedPreferences("paired_tv",MODE_PRIVATE);
        I18n.chinese=prefs.getBoolean("chinese",java.util.Locale.getDefault().getLanguage().equals("zh"));
        server=prefs.getString("server",""); token=prefs.getString("pair","");
        // Pair only the initial installation; later launch intents cannot overwrite it.
        String proposed=getIntent().getStringExtra("server"), pair=getIntent().getStringExtra("pair");
        if(server.isEmpty() && proposed!=null && pair!=null && pair.length()>=32 && validServer(proposed)) {
            server=proposed; token=pair;
            prefs.edit().putString("server",server).putString("pair",token).apply();
        }
        String previousPair=getIntent().getStringExtra("previousPair");
        if(!server.isEmpty()&&server.equals(proposed)&&previousPair!=null&&previousPair.equals(token)&&pair!=null&&pair.length()>=32){token=pair;prefs.edit().putString("pair",token).apply();}
        selectedId=prefs.getString("home-selected","");
        try { JSONObject cached=new JSONObject(prefs.getString("cache","{\"items\":[]} "));items=cached.getJSONArray("items");revision=cached.optInt("revision",-1); } catch(Exception ignored) {}
        home(I18n.t("正在连接你的片单…","Connecting to your library…")); refresh();clock.postDelayed(syncLoop,30000);clock.post(playerLoop);
    }

    static boolean validServer(String value) {
        try { URI uri=new URI(value); String host=uri.getHost();
            if(!"http".equals(uri.getScheme()) || host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || (uri.getPath()!=null&&!uri.getPath().isEmpty()) || uri.getPort()<1024 || uri.getPort()>65535)return false;
            String[] parts=host.split("\\."); if(parts.length!=4)return false;
            int[] ip=new int[4];for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;ip[i]=Integer.parseInt(parts[i]);if(ip[i]>255)return false;}
            return ip[0]==10 || (ip[0]==172&&ip[1]>=16&&ip[1]<=31) || (ip[0]==192&&ip[1]==168);
        }catch(Exception e){return false;}
    }

    private int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(8));return t;}
    private String rating(JSONObject item){
        JSONObject d=item.optJSONObject("douban");
        if(d==null||d.optString("url").isEmpty())return item.optString("category").equals("电影")||item.optString("category").equals("剧集")?I18n.t("豆瓣评分待核实","Douban not checked"):"";
        return d.isNull("rating")?I18n.t("豆瓣暂无评分","No Douban rating"):String.format(java.util.Locale.US,I18n.t("豆瓣 %.1f / 10","Douban %.1f / 10"),d.optDouble("rating"));
    }
    private String status(JSONObject item){String s=item.optString("status");return s.equals("watched")?I18n.t("看过","Watched"):s.equals("watching")?I18n.t("正在看","Watching"):s.equals("inbox")?I18n.t("待确认","Inbox"):I18n.t("想看","Watchlist");}
    private void home(String notice){
        generation++;video=null;detailItem=null;screen="home";
        if(detailView!=null)detailView.dismissSources();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(artworkLoader==null)artworkLoader=new ArtworkLoader(this,server,token);
        if(homeView==null)homeView=new HomeScreen(this,new HomeScreen.Host(){
            public void selected(JSONObject item){selectedId=item.optString("id");prefs.edit().putString("home-selected",selectedId).apply();}
            public void details(JSONObject item){selectedId=item.optString("id");showDetail(item);}
            public void play(JSONObject item){playSelected(item);}
            public void tools(){showTools();}
            public boolean canPlay(JSONObject item){return !primaryUrl(item).isEmpty();}
            public String availability(JSONObject item){return homeAvailability(item);}
            public String rating(JSONObject item){return MainActivity.this.rating(item);}
        },artworkLoader,selectedId);
        setContentView(homeView);homeView.applyItems(items);homeView.connection(connected,notice);homeView.restoreFocus();
    }
    private String primaryUrl(JSONObject item){
        JSONArray sources=item.optJSONArray("sources");
        if(sources!=null)for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);if(s!=null&&isOnline(s.optString("url")))return s.optString("url");}
        JSONObject p=item.optJSONObject("playback");String path=p==null?"":p.optString("path");
        if(!server.isEmpty()&&path.matches("/media/[0-9a-f]{64}\\.mp4"))return server+path;
        if(sources!=null)for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);if(s!=null&&"direct".equals(s.optString("kind"))&&s.optString("url").matches("https?://.+"))return s.optString("url");}
        return "";
    }
    private String homeAvailability(JSONObject item){
        if(!primaryUrl(item).isEmpty())return I18n.t("有播放入口","Playback source available");
        JSONObject prep=item.optJSONObject("preparation");String stage=prep==null?"":prep.optString("stage");
        if(stage.equals("preparing"))return I18n.t("正在准备，完成后会出现播放入口","Preparing media. Playback appears when ready.");
        if(stage.equals("searching"))return I18n.t("正在找片源","Searching for a source");
        if(item.optString("status").equals("inbox"))return I18n.t("待确认是否加入想看","Waiting for your choice");
        return I18n.t("已收藏，播放资源尚未准备好","Saved. Media is not ready yet.");
    }
    private void playSelected(JSONObject item){
        if(!connected){message(I18n.t("电脑暂时没有连接，请确认电脑开机后再试。","Computer offline. Keep it running and try again."));return;}
        String url=primaryUrl(item);if(url.isEmpty()){showDetail(item);return;}
        play(Uri.parse(url),item.optString("title"),item);
    }
    private void showTools(){
        new android.app.AlertDialog.Builder(this).setTitle(I18n.t("片单工具","Library tools")).setItems(new String[]{I18n.t("刷新片单","Refresh library"),I18n.t("电脑播放测试","Playback test"),"中文 / English"},(dialog,which)->{if(which==0)refresh();else if(which==1)playTest();else{prefs.edit().putBoolean("chinese",!I18n.chinese).apply();recreate();}}).setNegativeButton(I18n.t("返回","Back"),null).show();
    }
    private void refreshDetail(JSONObject updated){
        detailItem=updated;
        detailView.bind(updated,detailActions(updated),rating(updated),preparation(updated),connected);
    }
    private void refresh(){
        if(loading)return;
        if(server.isEmpty()){connected=false;if(homeView!=null)homeView.connection(false,I18n.t("尚未连接电脑，请让助手完成首次配对","Not paired. Follow the installation guide or ask your assistant."));return;}
        loading=true;
        io.execute(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(server+"/library").openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setRequestProperty("X-Watchroom-Pair",token);
                if(c.getResponseCode()!=200)throw new IOException("Connection unavailable");
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>2000000)throw new IOException("Library too large");}}
                String raw=out.toString("UTF-8");JSONObject body=new JSONObject(raw);JSONArray updated=body.getJSONArray("items");int nextRevision=body.optInt("revision");
                runOnUiThread(()->{loading=false;if(isFinishing())return;boolean changed=revision!=nextRevision;revision=nextRevision;items=updated;connected=true;if(screen.equals("detail")&&detailView!=null)detailView.connection(true);if(!raw.equals(prefs.getString("cache","")))prefs.edit().putString("cache",raw).apply();if(screen.equals("home")&&homeView!=null){homeView.connection(true,"");if(changed)homeView.applyItems(items);}else if(changed&&screen.equals("detail")&&detailItem!=null){String id=detailItem.optString("id");JSONObject latest=null;for(int n=0;n<items.length();n++){JSONObject x=items.optJSONObject(n);if(x!=null&&id.equals(x.optString("id"))){latest=x;break;}}if(latest==null)home(I18n.t("这部内容已从片单移除","This item was removed."));else if(!latest.toString().equals(detailItem.toString()))refreshDetail(latest);}});
            }catch(Exception e){runOnUiThread(()->{loading=false;connected=false;if(!isFinishing()&&screen.equals("detail")&&detailView!=null)detailView.connection(false);if(!isFinishing()&&screen.equals("home")&&homeView!=null)homeView.connection(false,I18n.t("电脑暂时连不上，显示上次保存的片单","Computer offline. Showing saved library."));});}finally{if(c!=null)c.disconnect();}
        });
    }
    private void showDetail(JSONObject item){
        screen="detail";detailItem=item;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(artworkLoader==null)artworkLoader=new ArtworkLoader(this,server,token);
        if(detailView==null)detailView=new DetailScreen(this,artworkLoader,()->home(""));
        detailView.setManagement(this::showManagement);
        detailView.bind(item,detailActions(item),rating(item),preparation(item),connected);
        setContentView(detailView);detailView.restoreFocus();
    }
    private void showManagement(){
        if(detailItem==null||managing)return;
        if(!connected){message(I18n.t("电脑暂时未连接，请电脑开机后再删除。","Connect the computer before deleting."));return;}
        final String id=detailItem.optString("id"),title=detailItem.optString("title");final int currentRevision=revision;
        new android.app.AlertDialog.Builder(this).setTitle(I18n.t("管理 · ","Manage · ")+title).setItems(new String[]{I18n.t("删除","Delete")},(dialog,which)->{
            if(detailItem==null||!id.equals(detailItem.optString("id")))return;
            try{JSONObject body=new JSONObject().put("id",id).put("revision",currentRevision);
                manageRequest("delete-preview",body,result->{
                    if(detailItem==null||!id.equals(detailItem.optString("id")))return;
                    if(result.optBoolean("deleted")){refresh();home(I18n.t("这部影片已删除","This item was deleted."));return;}
                    String summary=I18n.t("同时删除这张卡片和它关联的本机文件（","Remove this card and its registered local files (")+result.optInt("file_count")+I18n.t("个，约"," files, about ")+String.format(java.util.Locale.US,"%.2f",result.optDouble("bytes")/1e9)+I18n.t("GB）。"," GB).")+(result.optInt("shared_files")>0?I18n.t("其他影片共用的文件会保留。","Shared files are retained."):"");
                    android.app.AlertDialog confirm=new android.app.AlertDialog.Builder(this).setTitle(I18n.t("删除《","Delete: ")+title+I18n.t("》？","?")).setMessage(summary).setNegativeButton(I18n.t("取消","Cancel"),null).setPositiveButton(I18n.t("确认删除","Confirm deletion"),(d,w)->{
                        try{JSONObject request=new JSONObject().put("id",id).put("revision",result.getInt("revision")).put("confirmation",result.getString("confirmation"));
                            manageRequest("delete",request,receipt->{
                                if(!receipt.optBoolean("deleted"))return;
                                JSONArray kept=new JSONArray();for(int n=0;n<items.length();n++){JSONObject movie=items.optJSONObject(n);if(movie!=null&&!id.equals(movie.optString("id")))kept.put(movie);}items=kept;revision=receipt.optInt("revision",revision);
                                try{prefs.edit().remove("position:"+id).remove("online-position:"+id).putString("cache",new JSONObject().put("items",items).put("revision",revision).toString()).apply();}catch(JSONException ignored){}
                                home(I18n.t("已删除影片及关联的本机文件","Item and registered files deleted."));refresh();
                            });
                        }catch(JSONException e){message(I18n.t("删除信息已有变化，请重试。","Deletion preview changed. Try again."));}
                    }).create();confirm.setOnShowListener(d->confirm.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus());confirm.show();
                });
            }catch(JSONException e){message(I18n.t("删除信息已有变化，请重试。","Deletion preview changed. Try again."));}
        }).setNegativeButton(I18n.t("返回","Back"),null).show();
    }
    private interface ManageResult{void accept(JSONObject result);}
    private void manageRequest(String action,JSONObject body,ManageResult done){
        if(managing)return;managing=true;
        io.execute(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(server+"/manage/"+action).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("X-Watchroom-Pair",token);c.setConnectTimeout(6000);c.setReadTimeout(15000);
            byte[] bytes=body.toString().getBytes("UTF-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int code=c.getResponseCode();ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream input=code==200?c.getInputStream():c.getErrorStream();if(input==null)throw new IOException();try(InputStream in=input){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>32768)throw new IOException();}}
            JSONObject result=new JSONObject(out.toString("UTF-8"));runOnUiThread(()->{managing=false;if(isFinishing())return;if(code==200)done.accept(result);else{message(result.optString("error",I18n.t("删除未完成，请重试。","Deletion incomplete. Try again.")));refresh();}});
        }catch(Exception e){runOnUiThread(()->{managing=false;if(!isFinishing()){message(I18n.t("电脑暂时无法完成删除，请连接后重试。","Deletion unavailable. Reconnect and retry."));refresh();}});}finally{if(c!=null)c.disconnect();}});
    }
    private void detailPlay(String url,JSONObject item){
        if(!connected){message(I18n.t("电脑暂时没有连接，请确认电脑开机后再试。","Computer offline. Keep it running and try again."));return;}
        play(Uri.parse(url),item.optString("title"),item);
    }
    private java.util.List<DetailScreen.Action> detailActions(JSONObject item){
        java.util.ArrayList<DetailScreen.Action> actions=new java.util.ArrayList<>();
        JSONArray sources=item.optJSONArray("sources");boolean hasOnline=false;
        if(sources!=null)for(int i=0;i<sources.length();i++){
            JSONObject source=sources.optJSONObject(i);if(source==null)continue;
            final String url=source.optString("url");
            if(isOnline(url)){hasOnline=true;actions.add(new DetailScreen.Action("online:"+source.optString("id",url),url.contains("/vod/")?I18n.t("在线播放 · 1080p","Online playback"):I18n.t("在线播放 · PC中转","Online · PC relay"),I18n.t("在线播放","Play online"),()->detailPlay(url,item)));}
        }
        JSONObject playback=item.optJSONObject("playback");
        if(playback!=null&&!playback.optString("path").isEmpty()){
            final String url=server+playback.optString("path");
            actions.add(new DetailScreen.Action("local",hasOnline?I18n.t("播放本地备用版","Play local backup"):I18n.t("播放本地版","Play local media"),item.optString("category").equals("电影")?I18n.t("播放正片","Play film"):I18n.t("播放","Play"),()->detailPlay(url,item)));
        }
        if(sources!=null)for(int i=0;i<sources.length();i++){
            JSONObject source=sources.optJSONObject(i);if(source==null)continue;
            final String kind=source.optString("kind"),url=source.optString("url"),label=source.optString("label",I18n.t("观看入口","Source"));
            if(isOnline(url)||((hasOnline||playback!=null)&&!kind.equals("direct")))continue;
            actions.add(new DetailScreen.Action("source:"+source.optString("id",url),(kind.equals("direct")?I18n.t("播放 · ","Play · "):I18n.t("打开入口 · ","Open · "))+label,kind.equals("direct")?I18n.t("播放","Play"):I18n.t("打开观看入口","Open source"),()->{
                if(kind.equals("direct"))detailPlay(url,item);
                else if(kind.equals("local"))message(I18n.t("这条内容目前只记录了电脑文件位置，尚未接入播放。","Local path only. Prepare media before playback."));
                else openPage(url);
            }));
        }
        JSONObject prep=item.optJSONObject("preparation");
        if(prep==null||prep.optString("stage").isEmpty()){
            String[][] apps={{I18n.t("银河奇异果","iQIYI TV"),"com.gitvdemo.video"},{I18n.t("云视听极光","Tencent Video TV"),"com.ktcp.video"},{I18n.t("云视听小电视","Bilibili TV"),"com.xiaodianshi.tv.yst"}};
            for(String[] app:apps)if(launchIntent(app[1])!=null)actions.add(new DetailScreen.Action("app:"+app[1],I18n.t("打开 ","Open ")+app[0]+I18n.t("（在平台内搜索）"," (search in the app)"),I18n.t("打开 ","Open ")+app[0],()->launch(app[1])));
        }
        return actions;
    }
    private Intent launchIntent(String name){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(name);return i!=null?i:getPackageManager().getLaunchIntentForPackage(name);}
    private void launch(String name){try{Intent intent=launchIntent(name);if(intent==null)throw new Exception();startActivity(intent);}catch(Exception e){message(I18n.t("电视没有可打开的这个应用。","App not installed on this TV."));}}
    private void openPage(String url){
        Uri uri=Uri.parse(url);String host=uri.getHost();if(host==null||!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))){message(I18n.t("这个入口还不能在电视打开。","Cannot open this source on the TV."));return;}
        String pkg=(host.equals("iqiyi.com")||host.endsWith(".iqiyi.com"))?"com.gitvdemo.video":(host.equals("qq.com")||host.endsWith(".qq.com"))?"com.ktcp.video":(host.equals("bilibili.com")||host.endsWith(".bilibili.com"))?"com.xiaodianshi.tv.yst":null;
        Intent i=new Intent(Intent.ACTION_VIEW,uri);if(pkg!=null)i.setPackage(pkg);
        try{startActivity(i);}catch(Exception e){if(pkg!=null){message(I18n.t("平台未接受这个链接，请在平台内搜索片名。","Link not supported. Search in the app."));launch(pkg);}else message(I18n.t("电视没有能打开此网页的应用，请先在电脑端补充电视可用的播放来源。","No app can open this page. Add a playable source on the computer."));}
    }
    private boolean isOnline(String url){return false;}
    private String preparation(JSONObject item){JSONArray sources=item.optJSONArray("sources");if(sources!=null)for(int n=0;n<sources.length();n++){JSONObject source=sources.optJSONObject(n);if(source!=null&&isOnline(source.optString("url")))return item.optJSONObject("playback")!=null?I18n.t("在线播放优先 · 本地版备用","Online first · local backup"):I18n.t("在线播放 · PC中转","Online · PC relay");}JSONObject p=item.optJSONObject("preparation");String stage=p==null?"saved":p.optString("stage");return stage.equals("ready")?I18n.t("本地资源已准备","Local media prepared"):stage.equals("searching")?I18n.t("正在找片","Searching"):stage.equals("needs_access")?I18n.t("已收藏 · 等待来源接入","Saved · access needed"):stage.equals("preparing")?I18n.t("正在准备播放","Preparing playback"):stage.equals("unavailable")?I18n.t("已收藏 · 暂无可用来源","Saved · source unavailable"):I18n.t("已收藏 · 来源待核验","Saved · source pending");}
    private String cue(JSONArray list,int at){StringBuilder s=new StringBuilder();for(int i=0;i<list.length();i++){JSONObject c=list.optJSONObject(i);if(c==null)continue;if(c.optInt("start")>at)break;if(c.optInt("end")>at){if(s.length()>0)s.append("\n");s.append(c.optString("text"));}}return s.toString();}
    private void savePosition(){if(!onlineMirror&&detailItem!=null&&video!=null&&video.getDuration()>0){int t=video.getCurrentPosition();prefs.edit().putInt((onlineVod?"online-position:":"position:")+detailItem.optString("id"),t>=video.getDuration()-5000?0:t).apply();}}
    private void loadSubtitles(String path,int run){
        zh=new JSONArray();en=new JSONArray();if(path.isEmpty()||!path.startsWith("/media/"))return;
        io.execute(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(server+path).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setRequestProperty("X-Watchroom-Pair",token);
            if(c.getResponseCode()!=200)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4000000)throw new IOException();}}
            JSONObject data=new JSONObject(out.toString("UTF-8"));runOnUiThread(()->{if(run==generation){zh=data.optJSONArray("zh")==null?new JSONArray():data.optJSONArray("zh");en=data.optJSONArray("en")==null?new JSONArray():data.optJSONArray("en");}});
        }catch(Exception e){runOnUiThread(()->{if(run==generation)message(I18n.t("字幕暂时无法加载，请返回后重试。","Subtitles unavailable. Go back and retry."));});}finally{if(c!=null)c.disconnect();}});
    }
    private void playTest(){
        if(server.isEmpty()){play(Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.test_clip),getString(R.string.app_name)+I18n.t(" · 本地播放测试"," · Built-in playback test"),null);return;}
        message(I18n.t("正在检查电脑到电视的连接…","Checking the computer connection…"));
        io.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(server+"/media/test-stream.mp4").openConnection();c.setRequestProperty("X-Watchroom-Pair",token);c.setRequestProperty("Range","bytes=0-8388607");c.setConnectTimeout(6000);c.setReadTimeout(15000);long begin=System.nanoTime(),bytes=0;try(InputStream in=c.getInputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)bytes+=n;}double rate=bytes*8.0/((System.nanoTime()-begin)/1e9)/1e6;Log.i("Watchroom","lan_mbps="+rate+" bytes="+bytes);runOnUiThread(()->{if(!isFinishing()&&foreground){play(Uri.parse(server+"/media/test-stream.mp4"),I18n.t("电脑播放测试 · 约 ","PC playback test · about ")+Math.round(rate)+" Mbps",null);}});}catch(Exception e){runOnUiThread(()->message(I18n.t("电脑测试视频暂时不可用，请确认电脑服务运行。","Test video unavailable. Check the computer service.")));}finally{if(c!=null)c.disconnect();}});
    }
    private void play(Uri uri,String title,JSONObject item){
        if(playerControls!=null){playerControls.close();playerControls=null;}
        onlineVod=isOnline(uri.toString())&&uri.toString().contains("/vod/");onlineMirror=isOnline(uri.toString())&&!onlineVod;onlineStopUrl=isOnline(uri.toString())?uri.toString().substring(0,uri.toString().lastIndexOf('/')+1)+"stop":"";detailItem=item;screen="player";int run=++generation;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);frame.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);frame.addView(video,vp);
        LinearLayout subtitles=new LinearLayout(this);subtitles.setOrientation(LinearLayout.VERTICAL);subtitles.setGravity(Gravity.CENTER);subtitles.setPadding(dp(35),dp(10),dp(35),dp(28));subtitleZh=text("",26,WHITE);subtitleEn=text("",23,GOLD);for(TextView t:new TextView[]{subtitleZh,subtitleEn}){t.setGravity(Gravity.CENTER);t.setShadowLayer(4,2,2,Color.BLACK);subtitles.addView(t);}frame.addView(subtitles,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(frame);lastReported=-1;zh=new JSONArray();en=new JSONArray();
        playerControls=new PlayerControls(this,video,clock,title,frame);
        playerControls.live(onlineMirror);
        if(item!=null&&!onlineMirror&&!onlineVod){JSONObject p=item.optJSONObject("playback");if(p!=null)loadSubtitles(p.optString("subtitles"),run);}
        VideoView playing=video;playing.setOnPreparedListener(mp->{if(run!=generation)return;mp.setLooping(false);mp.setOnSeekCompleteListener(done->{if(run==generation&&playerControls!=null)playerControls.seekComplete();});int resume=item==null||onlineMirror?0:prefs.getInt((onlineVod?"online-position:":"position:")+item.optString("id"),0);if(resume>0&&resume<playing.getDuration()-5000)playing.seekTo(resume);playing.start();Log.i("Watchroom","prepared duration_ms="+playing.getDuration()+" width="+mp.getVideoWidth()+" height="+mp.getVideoHeight());playerControls.prepared(mp);});
        playing.setOnInfoListener((mp,what,extra)->{if(run==generation&&playerControls!=null){if(what==701)playerControls.buffering(true);else if(what==702)playerControls.buffering(false);}return false;});
        playing.setOnErrorListener((mp,what,extra)->{if(run==generation){Log.i("Watchroom","player_error="+what+" extra="+extra);message(I18n.t("这条视频暂时无法播放，可能需要登录或格式不受支持。","Cannot play this video. Login may be needed or the format may be unsupported."));exitPlayer();}return true;});
        playing.setOnCompletionListener(mp->{if(run==generation)exitPlayer();});
        if(mediaProxy!=null){mediaProxy.close();mediaProxy=null;}
        if(uri.toString().startsWith(server+"/media/")||onlineMirror||onlineVod){try{mediaProxy=new LocalMediaProxy(uri.toString(),token);playing.setVideoURI(Uri.parse(mediaProxy.url()));}catch(IOException e){message(I18n.t("播放连接暂时不可用。","Playback connection unavailable."));exitPlayer();return;}}else playing.setVideoURI(uri);playing.requestFocus();
    }
    private void stopMirror(){if(onlineStopUrl.isEmpty())return;onlineMirror=false;onlineVod=false;final String stopUrl=onlineStopUrl;onlineStopUrl="";io.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(stopUrl).openConnection();c.setRequestProperty("X-Watchroom-Pair",token);c.setConnectTimeout(4000);c.setReadTimeout(4000);c.getResponseCode();}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}
    private void exitPlayer(){JSONObject item=detailItem;savePosition();stopMirror();generation++;if(playerControls!=null){playerControls.close();playerControls=null;}if(video!=null){video.stopPlayback();video=null;}if(mediaProxy!=null){mediaProxy.close();mediaProxy=null;}if(item!=null)showDetail(item);else home("");}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(screen.equals("player")&&playerControls!=null&&playerControls.key(e))return true;
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){if(screen.equals("player")){if(playerControls==null||!playerControls.cancelSeek())exitPlayer();}else if(screen.equals("detail"))home("");else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();foreground=true;if(prefs!=null&&!screen.equals("player"))refresh();}
    @Override protected void onPause(){foreground=false;savePosition();super.onPause();if(video!=null&&video.isPlaying())video.pause();if(playerControls!=null)playerControls.hostPaused();}
    @Override protected void onDestroy(){generation++;if(detailView!=null)detailView.dismissSources();if(artworkLoader!=null)artworkLoader.close();if(playerControls!=null)playerControls.close();clock.removeCallbacksAndMessages(null);if(video!=null)video.stopPlayback();if(mediaProxy!=null)mediaProxy.close();io.shutdownNow();super.onDestroy();}
    private void message(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
