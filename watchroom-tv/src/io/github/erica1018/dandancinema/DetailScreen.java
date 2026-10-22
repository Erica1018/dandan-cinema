package io.github.erica1018.dandancinema;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Retained detail surface. Artwork and actions always come from the current library item. */
public final class DetailScreen extends FrameLayout {
    public interface Host { void back(); }
    public static final class Action {
        public final String key,label,shortLabel;
        public final Runnable run;
        public Action(String key,String label,String shortLabel,Runnable run){this.key=key;this.label=label;this.shortLabel=shortLabel;this.run=run;}
    }
    private static final int BACK=Color.rgb(18,26,23), PANEL=Color.rgb(38,53,46), GOLD=Color.rgb(229,235,143), WHITE=Color.rgb(242,244,233), MUTED=Color.rgb(174,189,176);
    private final float scale;
    private final ArtworkLoader art;
    private final ImageView image;
    private final TextView eyebrow,title,english,score,availability,synopsis,note,checked,resource,message,help;
    private final Button back,primary,more,manage;
    private Runnable management;
    private final ScrollView storyScroll,resourceScroll;
    private final ArrayList<Action> actions=new ArrayList<>();
    private String itemId="",savedAvailability="";
    private Dialog chooser;

    public DetailScreen(Context context,ArtworkLoader art,Host host){
        super(context);this.art=art;
        android.util.DisplayMetrics dm=context.getResources().getDisplayMetrics();scale=Math.min(dm.widthPixels/1920f,dm.heightPixels/1080f);
        setBackgroundColor(BACK);setFocusable(false);
        image=new ImageView(context);image.setScaleType(ImageView.ScaleType.CENTER_CROP);place(image,0,0,1920,830);
        View left=new View(context);left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{BACK,0xF5121A17,0x9A121A17,0x00121A17}));place(left,0,0,1540,830);
        View bottom=new View(context);bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00121A17,BACK,BACK}));place(bottom,0,470,1920,390);
        back=button(I18n.t("返回片单","Back to library"),false,host::back);back.setTag("detail:back");back.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(26));place(back,88,46,186,62);
        ImageView brand=new ImageView(context);brand.setImageResource(R.drawable.brand_badge);brand.setScaleType(ImageView.ScaleType.FIT_CENTER);brand.setContentDescription(context.getString(R.string.app_name)+I18n.t("标志","logo"));brand.setBackground(surface(BACK,false,12));brand.setClipToOutline(true);place(brand,302,44,66,66);
        place(label(context.getString(R.string.app_name)+I18n.t("  /  内容详情","  /  Details"),27,MUTED,false),388,62,900,42);
        manage=button(I18n.t("管理","Manage"),false,()->{if(management!=null)management.run();});manage.setTag("detail:manage");manage.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(26));place(manage,1664,46,168,62);
        manage.setNextFocusLeftId(back.getId());back.setNextFocusRightId(manage.getId());
        eyebrow=label("",28,MUTED,false);place(eyebrow,90,177,980,44);
        title=label("",92,WHITE,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setTag("detail:title");place(title,88,235,1050,168);
        english=label("",31,MUTED,false);english.setLetterSpacing(.12f);english.setSingleLine();place(english,92,407,990,48);
        score=label("",30,GOLD,false);place(score,92,463,990,46);
        primary=button(I18n.t("播放","Play"),true,()->{if(!actions.isEmpty())invoke(actions.get(0).key);});primary.setTag("detail:primary");place(primary,88,535,300,84);
        more=button(I18n.t("其他播放方式","Other sources"),false,this::showSources);more.setTag("detail:more");place(more,406,535,300,84);
        availability=label("",25,MUTED,false);availability.setSingleLine();availability.setEllipsize(TextUtils.TruncateAt.END);place(availability,92,643,1670,40);
        View divider=new View(context);divider.setBackgroundColor(0x484E6254);place(divider,88,703,1744,1);
        place(label(I18n.t("简介","Synopsis"),29,WHITE,true),100,734,960,44);place(label(I18n.t("播放与资源","Playback and sources"),29,WHITE,true),1208,734,620,44);
        LinearLayout story=new LinearLayout(context);story.setOrientation(LinearLayout.VERTICAL);
        synopsis=label("",29,WHITE,false);synopsis.setLineSpacing(px(9),1);synopsis.setTag("detail:synopsis");story.addView(synopsis,new LinearLayout.LayoutParams(-1,-2));
        note=label("",24,MUTED,false);note.setLineSpacing(px(7),1);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=px(20);story.addView(note,np);
        checked=label("",21,MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=px(20);story.addView(checked,cp);
        storyScroll=readingPane(story,I18n.t("简介","Synopsis"));storyScroll.setTag("detail:story");place(storyScroll,88,787,1034,215);
        LinearLayout resources=new LinearLayout(context);resources.setOrientation(LinearLayout.VERTICAL);
        resource=label("",26,GOLD,false);resource.setLineSpacing(px(7),1);resources.addView(resource,new LinearLayout.LayoutParams(-1,-2));
        message=label("",25,MUTED,false);message.setLineSpacing(px(7),1);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=px(17);resources.addView(message,mp);
        resourceScroll=readingPane(resources,I18n.t("播放与资源","Playback and sources"));resourceScroll.setTag("detail:resources");place(resourceScroll,1196,787,648,215);
        help=label(I18n.t("确认播放  ·  向下阅读  ·  返回片单","OK play · Down read · Back library"),23,MUTED,false);place(help,88,1026,1720,35);
        primary.setNextFocusRightId(more.getId());more.setNextFocusLeftId(primary.getId());
        for(Button b:new Button[]{primary,more})b.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){(v==more?resourceScroll:storyScroll).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){back.requestFocus();return true;}return false;});
        manage.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(actions.isEmpty())resourceScroll.requestFocus();else focusPrimary();return true;}return false;});
        back.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(actions.isEmpty())storyScroll.requestFocus();else focusPrimary();return true;}return false;});
    }
    private int px(float value){return Math.round(value*scale);}
    private void place(View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);addView(view,p);}
    private TextView label(String value,int size,int color,boolean bold){TextView v=new TextView(getContext());v.setText(value);v.setTextColor(color);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));v.setIncludeFontPadding(false);v.setGravity(Gravity.TOP|Gravity.LEFT);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable surface(int color,boolean focused,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));if(focused)d.setStroke(px(3),GOLD);return d;}
    private Button button(String value,boolean main,Runnable click){Button b=new Button(getContext());b.setId(View.generateViewId());b.setText(value);b.setAllCaps(false);b.setTextColor(main?BACK:WHITE);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(30));b.setTypeface(null,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setPadding(px(20),0,px(20),0);b.setFocusable(true);b.setBackground(surface(main?GOLD:0xDF26352E,false,44));b.setOnFocusChangeListener((v,focus)->{GradientDrawable d=surface(main?GOLD:0xDF26352E,false,44);if(focus)d.setStroke(px(4),main?WHITE:GOLD);v.setBackground(d);});b.setOnClickListener(v->click.run());return b;}
    private ScrollView readingPane(LinearLayout contents,String description){
        ScrollView scroll=new ScrollView(getContext());scroll.setId(View.generateViewId());scroll.setFocusable(true);scroll.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);scroll.setVerticalScrollBarEnabled(true);scroll.setOverScrollMode(OVER_SCROLL_NEVER);scroll.setPadding(px(12),px(5),px(12),px(12));scroll.setContentDescription(description+I18n.t("，上下键阅读",", Up/Down to read"));scroll.addView(contents,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnFocusChangeListener((v,focus)->{v.setBackground(surface(0,focus,12));help.setText(focus?I18n.t("上下阅读  ·  左右切换内容  ·  返回片单","Up/Down read · Left/Right sections · Back library"):actionHelp());});
        scroll.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){(scroll==storyScroll?resourceScroll:storyScroll).requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP&&scroll.getScrollY()==0){focusPrimary();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int direction=key==KeyEvent.KEYCODE_DPAD_UP?-1:1;scroll.smoothScrollBy(0,direction*px(90));return true;}
            return false;
        });return scroll;
    }
    private static void setText(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private void optional(TextView view,String value){setText(view,value);view.setVisibility(value.isEmpty()?GONE:VISIBLE);}
    private String status(JSONObject item){String s=item.optString("status");return s.equals("watched")?I18n.t("看过","Watched"):s.equals("watching")?I18n.t("正在看","Watching"):s.equals("inbox")?I18n.t("待确认","Inbox"):I18n.t("想看","Watchlist");}
    public void bind(JSONObject item,List<Action> updated,String rating,String preparation,boolean connected){
        boolean same=itemId.equals(item.optString("id"));itemId=item.optString("id");actions.clear();actions.addAll(updated);
        String raw=item.optString("title"),clean=raw.replaceAll("[（(](?:19|20)[0-9]{2}[）)]","").trim(),en="";
        java.util.regex.Matcher bilingual=java.util.regex.Pattern.compile("^(.+?[\\u4e00-\\u9fff])\\s+([A-Za-z][A-Za-z .'-]*)$").matcher(clean);if(bilingual.matches()){clean=bilingual.group(1);en=bilingual.group(2).toUpperCase(Locale.ROOT);}
        setText(title,clean);title.setContentDescription(raw);title.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(clean.length()>17?58:clean.length()>10?72:92));setText(english,en);
        java.util.regex.Matcher year=java.util.regex.Pattern.compile("[（(]((?:19|20)[0-9]{2})[）)]").matcher(raw);setText(eyebrow,I18n.category(item.optString("category"))+(year.find()?"  ·  "+year.group(1):"")+"  ·  "+status(item));setText(score,rating);
        JSONObject artwork=item.optJSONObject("artwork");art.load(artwork==null?"":artwork.optString("hero"),image);
        setText(synopsis,item.optString("synopsis").isEmpty()?I18n.t("这部内容还没有简介。","No synopsis yet."):item.optString("synopsis"));optional(note,item.optString("note").isEmpty()?"":I18n.t("备注  ·  ","Notes · ")+item.optString("note"));
        JSONObject douban=item.optJSONObject("douban");optional(checked,douban==null||douban.optString("checked_on").isEmpty()?"":I18n.t("豆瓣评分核验于 ","Douban checked on ")+douban.optString("checked_on"));
        savedAvailability=preparation;connection(connected);
        setText(resource,preparation);JSONObject prep=item.optJSONObject("preparation");optional(message,prep==null?"":prep.optString("message"));
        boolean primaryFocused=primary.hasFocus(),moreFocused=more.hasFocus();primary.setVisibility(actions.isEmpty()?GONE:VISIBLE);if(!actions.isEmpty())setText(primary,actions.get(0).shortLabel);more.setVisibility(actions.size()>1?VISIBLE:GONE);
        if((primaryFocused&&actions.isEmpty())||(moreFocused&&actions.size()<2))focusPrimary();
        primary.setNextFocusRightId(actions.size()>1?more.getId():primary.getId());
        if(!storyScroll.hasFocus()&&!resourceScroll.hasFocus())setText(help,actionHelp());
        if(!same){dismissSources();storyScroll.scrollTo(0,0);resourceScroll.scrollTo(0,0);}
    }
    public void setManagement(Runnable action){management=action;}
    public void connection(boolean connected){setText(availability,connected?savedAvailability:I18n.t("电脑暂时未连接  ·  显示最近保存的资料","Computer offline · showing saved details"));}
    private String actionHelp(){return actions.isEmpty()?I18n.t("向下阅读  ·  返回片单","Down read · Back library"):(actions.get(0).shortLabel.startsWith(I18n.t("打开","Open"))?I18n.t("确认打开","OK open"):I18n.t("确认播放","OK play"))+I18n.t("  ·  向下阅读  ·  返回片单"," · Down read · Back library");}
    private void focusPrimary(){if(primary.getVisibility()==VISIBLE)primary.requestFocus();else back.requestFocus();}
    public void restoreFocus(){post(this::focusPrimary);}
    private void invoke(String key){for(Action action:actions)if(action.key.equals(key)){action.run.run();return;}Toast.makeText(getContext(),I18n.t("这个播放入口已更新，请重新选择。","Source changed. Please select again."),Toast.LENGTH_LONG).show();}
    private void showSources(){
        if(actions.size()<2)return;dismissSources();chooser=new Dialog(getContext());chooser.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(getContext());panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(px(36),px(30),px(36),px(30));panel.setBackground(surface(PANEL,false,24));
        TextView heading=label(I18n.t("选择播放方式","Choose a source"),34,WHITE,true);panel.addView(heading,new LinearLayout.LayoutParams(-1,px(68)));
        ScrollView list=new ScrollView(getContext());list.setVerticalScrollBarEnabled(false);LinearLayout rows=new LinearLayout(getContext());rows.setOrientation(LinearLayout.VERTICAL);list.addView(rows);panel.addView(list,new LinearLayout.LayoutParams(-1,px(Math.min(actions.size()*92,460))));
        Button first=null;for(Action action:actions){final String key=action.key;Button b=button(action.label,false,()->{dismissSources();invoke(key);});b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(28));b.setTag("detail:source:"+key);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(76));p.bottomMargin=px(16);rows.addView(b,p);if(first==null)first=b;}
        TextView hint=label(I18n.t("上下选择  ·  确认打开  ·  返回关闭","Up/Down choose · OK open · Back close"),22,MUTED,false);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,px(42));hp.topMargin=px(22);panel.addView(hint,hp);
        chooser.setContentView(panel);Window window=chooser.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.72f;p.width=px(1000);p.height=-2;p.gravity=Gravity.CENTER;window.setAttributes(p);}chooser.setOnDismissListener(dialog->{if(more.getVisibility()==VISIBLE)more.requestFocus();});chooser.show();if(window!=null)window.setLayout(px(1000),-2);if(first!=null)first.requestFocus();
    }
    public void dismissSources(){if(chooser!=null){Dialog old=chooser;chooser=null;old.dismiss();}}
}
