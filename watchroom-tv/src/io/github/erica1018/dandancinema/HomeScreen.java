package io.github.erica1018.dandancinema;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.*;
import org.json.*;
import java.util.*;

/** A retained native home surface. Library changes reconcile cards by stable item ID. */
public final class HomeScreen extends FrameLayout {
    public interface Host {
        void selected(JSONObject item);
        void details(JSONObject item);
        void play(JSONObject item);
        void tools();
        boolean canPlay(JSONObject item);
        String availability(JSONObject item);
        String rating(JSONObject item);
    }
    private static final int BACK=Color.rgb(18,26,23), GOLD=Color.rgb(229,235,143), WHITE=Color.rgb(242,244,233), MUTED=Color.rgb(174,189,176);
    private final Host host;
    private final ArtworkLoader art;
    private final float scale;
    private final ImageView backdrop;
    private final TextView title,english,metadata,synopsis,availability,notice,credit,empty;
    private final View wantIndicator,allIndicator;
    private final Button play,details,want,all;
    private final ImageButton tools;
    private final HorizontalScrollView shelf;
    private final LinearLayout row;
    private final LinkedHashMap<String,Card> cards=new LinkedHashMap<>();
    private final HashMap<String,String> selections=new HashMap<>();
    private final HashMap<String,Integer> scrolls=new HashMap<>();
    private JSONArray items=new JSONArray();
    private JSONObject selected;
    private String filter="want", selectedId="", heroSignature="";
    private boolean connected=true,reconciling;
    private int updateGeneration;

    public HomeScreen(Context context,Host host,ArtworkLoader art,String initialId) {
        super(context);this.host=host;this.art=art;selectedId=initialId;
        android.util.DisplayMetrics dm=context.getResources().getDisplayMetrics();
        scale=Math.min(dm.widthPixels/1920f,dm.heightPixels/1080f);
        setBackgroundColor(BACK);setFocusable(false);
        backdrop=new ImageView(context);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);place(backdrop,0,0,1920,810);
        View left=new View(context);left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{BACK,0xF0121A17,0x40121A17,0x00121A17}));place(left,0,0,1480,810);
        View bottom=new View(context);bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00121A17,BACK}));place(bottom,0,530,1920,300);
        ImageView brand=new ImageView(context);brand.setImageResource(R.drawable.brand_badge);brand.setScaleType(ImageView.ScaleType.FIT_CENTER);brand.setContentDescription(context.getString(R.string.app_name)+I18n.t("标志","logo"));brand.setBackground(outline(BACK,false,14));brand.setClipToOutline(true);place(brand,86,30,86,86);
        TextView logo=label(context.getString(R.string.app_name),44,WHITE,true);place(logo,190,49,226,64);
        want=nav(I18n.t("想看","Watchlist"),()->setFilter("want"));all=nav(I18n.t("全部片单","All titles"),()->setFilter("all"));place(want,442,38,108,72);place(all,568,38,180,72);
        wantIndicator=new View(context);wantIndicator.setBackgroundColor(GOLD);place(wantIndicator,460,103,72,4);allIndicator=new View(context);allIndicator.setBackgroundColor(GOLD);place(allIndicator,586,103,144,4);
        tools=new ImageButton(context);tools.setImageResource(android.R.drawable.ic_menu_manage);tools.setColorFilter(MUTED);tools.setContentDescription(I18n.t("片单工具","Library tools"));tools.setPadding(px(15),px(15),px(15),px(15));tools.setFocusable(true);tools.setBackground(outline(0,false,12));tools.setOnFocusChangeListener((v,f)->v.setBackground(outline(0,f,12)));tools.setOnClickListener(v->host.tools());place(tools,1798,40,70,70);
        title=label("",108,WHITE,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);place(title,88,154,680,196);
        english=label("",29,MUTED,false);english.setLetterSpacing(.14f);english.setSingleLine();place(english,90,300,660,44);
        metadata=label("",30,MUTED,false);place(metadata,90,358,660,45);
        synopsis=label("",30,WHITE,false);synopsis.setMaxLines(2);synopsis.setEllipsize(TextUtils.TruncateAt.END);synopsis.setLineSpacing(px(8),1);place(synopsis,90,410,620,92);
        play=action(I18n.t("播放","Play"),true,()->{if(selected!=null)host.play(selected);});android.graphics.drawable.Drawable playIcon=getResources().getDrawable(android.R.drawable.ic_media_play);playIcon.setTint(BACK);playIcon.setBounds(0,0,px(36),px(36));play.setCompoundDrawables(playIcon,null,null,null);play.setCompoundDrawablePadding(px(16));play.setPadding(px(48),0,px(48),0);details=action(I18n.t("查看详情","Details"),false,()->{if(selected!=null)host.details(selected);});place(play,88,530,266,82);place(details,370,530,266,82);
        availability=label("",23,MUTED,false);place(availability,92,629,970,36);
        shelf=new HorizontalScrollView(context);shelf.setHorizontalScrollBarEnabled(false);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setFillViewport(false);shelf.setOverScrollMode(View.OVER_SCROLL_NEVER);shelf.setPadding(px(6),px(7),px(6),0);
        row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setClipChildren(false);row.setClipToPadding(false);shelf.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));place(shelf,78,668,1778,320);
        empty=label(I18n.t("这里还没有内容。告诉助手想看什么，准备好后会出现在这里。","Your library is empty. Add a title on your computer or ask your assistant."),31,MUTED,false);empty.setMaxLines(3);place(empty,88,730,1300,180);empty.setVisibility(GONE);
        TextView help=label(I18n.t("方向键选片  ·  确认查看  ·  返回上一页","Arrows browse · OK details · Back"),23,MUTED,false);place(help,88,1022,1130,36);
        notice=label("",21,MUTED,false);notice.setGravity(Gravity.RIGHT);notice.setSingleLine();notice.setEllipsize(TextUtils.TruncateAt.END);place(notice,940,1026,890,32);
        credit=label("",18,MUTED,false);credit.setGravity(Gravity.RIGHT);place(credit,1270,980,560,30);
        for(Button b:new Button[]{want,all,play,details}) b.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusSelected();return true;}
            return false;
        });
        play.setNextFocusRightId(details.getId());details.setNextFocusLeftId(play.getId());
        updateTabs();
    }
    private int px(float x){return Math.round(x*scale);}
    private void place(View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);addView(view,p);}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(color);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setIncludeFontPadding(false);t.setGravity(Gravity.TOP|Gravity.LEFT);t.setPadding(0,0,0,0);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable outline(int fill,boolean focus,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(px(radius));if(focus)d.setStroke(px(4),GOLD);return d;}
    private Button nav(String name,Runnable click){Button b=action(name,false,click);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(30));b.setPadding(0,0,0,0);b.setOnFocusChangeListener((v,f)->v.setBackground(outline(0,f,12)));return b;}
    private Button action(String name,boolean primary,Runnable click){Button b=new Button(getContext());b.setId(View.generateViewId());b.setText(name);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(31));b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?BACK:WHITE);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setPadding(px(16),0,px(16),0);b.setGravity(Gravity.CENTER);b.setBackground(outline(primary?GOLD:0xB026352E,false,42));b.setFocusable(true);b.setOnFocusChangeListener((v,f)->{GradientDrawable d=outline(primary?GOLD:0xB026352E,false,42);if(f)d.setStroke(px(4),primary?WHITE:GOLD);v.setBackground(d);});b.setOnClickListener(v->click.run());return b;}
    private void updateTabs(){want.setTextColor(filter.equals("want")?WHITE:MUTED);all.setTextColor(filter.equals("all")?WHITE:MUTED);want.setBackground(outline(0,want.hasFocus(),12));all.setBackground(outline(0,all.hasFocus(),12));wantIndicator.setVisibility(filter.equals("want")?VISIBLE:INVISIBLE);allIndicator.setVisibility(filter.equals("all")?VISIBLE:INVISIBLE);}
    public String selectedId(){return selectedId;}
    public String filter(){return filter;}
    public void connection(boolean available,String message){connected=available;setText(notice,message);refreshAvailability();}
    public void invalidateActions(){heroSignature="";if(selected!=null)showSelected(selected);}
    public void setFilter(String next){if(filter.equals(next)){focusSelected();return;}selections.put(filter,selectedId);scrolls.put(filter,shelf.getScrollX());filter=next;selectedId=selections.containsKey(next)?selections.get(next):"";selected=null;heroSignature="";updateTabs();applyItems(items);shelf.post(()->{Integer x=scrolls.get(next);if(x!=null)shelf.scrollTo(x,0);focusSelected();});}
    private boolean visible(JSONObject item){if(filter.equals("all"))return true;JSONArray tags=item.optJSONArray("tags");if(tags!=null)for(int n=0;n<tags.length();n++)if("system:test".equals(tags.optString(n)))return false;String s=item.optString("status");return s.equals("want")||s.equals("watching");}
    private static String signature(JSONObject item){return item==null?"":item.toString();}
    private static void setText(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}

    /** Unchanged cards are left attached; changed data is bound on its existing card. */
    public void applyItems(JSONArray updated){
        items=updated;reconciling=true;final int run=++updateGeneration;
        final View oldFocus=findFocus();final int oldScroll=shelf.getScrollX();final Card anchor=cards.get(selectedId);
        final int oldOffset=anchor==null?0:anchor.getLeft()-oldScroll;
        int oldIndex=anchor==null?0:row.indexOfChild(anchor);
        LinkedHashSet<String> keep=new LinkedHashSet<>();ArrayList<JSONObject> visible=new ArrayList<>();
        for(int i=0;i<updated.length();i++){JSONObject item=updated.optJSONObject(i);if(item==null||item.optString("id").isEmpty()||!visible(item)||!keep.add(item.optString("id")))continue;visible.add(item);}
        Collections.sort(visible,(a,b)->Boolean.compare(!a.optString("category").equals("电影"),!b.optString("category").equals("电影")));
        boolean structural=false;int created=0,changed=0,removed=0,moved=0;
        for(Iterator<Map.Entry<String,Card>> it=cards.entrySet().iterator();it.hasNext();){Map.Entry<String,Card> entry=it.next();if(!keep.contains(entry.getKey())){row.removeView(entry.getValue());it.remove();removed++;structural=true;}}
        for(int index=0;index<visible.size();index++){
            JSONObject item=visible.get(index);String id=item.optString("id");Card card=cards.get(id);
            if(card==null){card=new Card(item);cards.put(id,card);created++;structural=true;}
            else if(card.bind(item))changed++;
            int current=row.indexOfChild(card);
            if(current!=index){if(current>=0){row.removeView(card);moved++;}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(568),px(310));p.rightMargin=px(24);row.addView(card,index,p);structural=true;}
        }
        if(!keep.contains(selectedId))selectedId=visible.isEmpty()?"":visible.get(Math.min(Math.max(oldIndex,0),visible.size()-1)).optString("id");
        Card active=cards.get(selectedId);selected=active==null?null:active.item;
        for(Card card:cards.values())card.setSelected(card==active);
        if(selected!=null){showSelected(selected);host.selected(selected);}else clearSelection();
        empty.setVisibility(visible.isEmpty()?VISIBLE:GONE);shelf.setVisibility(visible.isEmpty()?GONE:VISIBLE);
        reconciling=false;
        final boolean needsLayout=structural;
        if(structural){row.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){row.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(run!=updateGeneration)return;Card current=cards.get(selectedId);boolean sameAnchor=current!=null&&current==anchor;if(oldFocus!=null&&isDescendant(oldFocus)&&oldFocus.isFocusable()&&oldFocus.getVisibility()==VISIBLE)oldFocus.requestFocus();else if(oldFocus==null||oldFocus==anchor)focusSelected();if(sameAnchor)shelf.scrollTo(Math.max(0,current.getLeft()-oldOffset),0);else if(current==null)shelf.scrollTo(oldScroll,0);}});}
        else if(oldFocus==null)focusSelected();
        android.util.Log.i("WatchroomUI","reconcile created="+created+" updated="+changed+" removed="+removed+" moved="+moved+" selected="+selectedId+" structural="+needsLayout);
    }
    private boolean isDescendant(View v){for(android.view.ViewParent p=v.getParent();p!=null;p=p.getParent())if(p==this)return true;return false;}
    private void clearSelection(){selected=null;heroSignature="";setText(title,I18n.t("想看的，都在这里。","Your next watch starts here."));setText(english,"");setText(metadata,"");setText(synopsis,"");setText(availability,"");setText(credit,"");play.setVisibility(GONE);details.setVisibility(GONE);art.load("",backdrop);}
    private void focusSelected(){Card card=cards.get(selectedId);if(card!=null)card.requestFocus();else want.requestFocus();}
    public void restoreFocus(){shelf.post(()->focusSelected());}
    private void refreshAvailability(){if(selected==null)return;setText(availability,connected?host.availability(selected):I18n.t("电脑暂时未连接，资源准备状态以最近同步为准","Computer offline. Showing last synced status."));}
    private void showSelected(JSONObject item){
        selected=item;String next=signature(item);if(next.equals(heroSignature)){refreshAvailability();return;}heroSignature=next;
        String original=item.optString("title"),clean=original.replaceAll("[（(](?:19|20)[0-9]{2}[）)]","").trim();
        String en="";java.util.regex.Matcher bilingual=java.util.regex.Pattern.compile("^(.+?[\\u4e00-\\u9fff])\\s+([A-Za-z][A-Za-z .'-]*)$").matcher(clean);
        if(bilingual.matches()){clean=bilingual.group(1);en=bilingual.group(2).toUpperCase(java.util.Locale.ROOT);}
        setText(title,clean);title.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(clean.length()>17?52:clean.length()>10?66:108));setText(english,en);
        java.util.regex.Matcher year=java.util.regex.Pattern.compile("[（(]((?:19|20)[0-9]{2})[）)]").matcher(original);String meta=(year.find()?year.group(1)+" · ":"")+I18n.category(item.optString("category"));String score=host.rating(item);if(!score.isEmpty())meta+="    "+score.replace(" / 10","");setText(metadata,meta);
        String description=item.optString("synopsis");java.util.regex.Matcher sentence=java.util.regex.Pattern.compile("^.*?[。！？]").matcher(description);if(sentence.find())description=sentence.group();setText(synopsis,description);
        JSONObject artwork=item.optJSONObject("artwork");art.load(artwork==null?"":artwork.optString("hero"),backdrop);setText(credit,artwork==null?"":artwork.optString("credit"));
        boolean ready=host.canPlay(item);boolean wasPrimary=play.hasFocus();play.setVisibility(ready?VISIBLE:GONE);details.setVisibility(VISIBLE);if(wasPrimary&&!ready)details.requestFocus();refreshAvailability();
    }
    private final class Card extends LinearLayout {
        JSONObject item;String bound="";final FrameLayout picture;final ImageView image;final TextView name,meta;
        Card(JSONObject value){super(HomeScreen.this.getContext());setTag("card:"+value.optString("id"));setId(View.generateViewId());setOrientation(VERTICAL);setFocusable(true);setFocusableInTouchMode(true);setClipChildren(false);setPadding(px(6),px(6),px(6),0);
            picture=new FrameLayout(getContext());picture.setPadding(px(5),px(5),px(5),px(5));picture.setBackground(outline(0xFF26352E,false,14));image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.addView(image,new FrameLayout.LayoutParams(-1,-1));addView(picture,new LinearLayout.LayoutParams(-1,px(228)));
            name=label("",29,WHITE,true);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(43));p.topMargin=px(9);p.leftMargin=px(9);addView(name,p);
            meta=label("",22,MUTED,false);meta.setSingleLine();meta.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(-1,px(32));m.leftMargin=px(9);addView(meta,m);
            setOnFocusChangeListener((v,focus)->{picture.setBackground(outline(0xFF26352E,focus,14));if(focus&&!reconciling){selectedId=item.optString("id");selections.put(filter,selectedId);showSelected(item);host.selected(item);for(Card other:cards.values())other.setSelected(other==this);}});
            setOnClickListener(v->host.details(item));setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP){if(play.getVisibility()==VISIBLE)play.requestFocus();else details.requestFocus();return true;}return false;});bind(value);
        }
        boolean bind(JSONObject value){item=value;String next=signature(value);if(next.equals(bound))return false;bound=next;setText(name,value.optString("title").replaceAll("[（(](?:19|20)[0-9]{2}[）)]","").trim());String score=host.rating(value);setText(meta,I18n.category(value.optString("category"))+(score.isEmpty()?"":"    "+score.replace(" / 10","")));JSONObject a=value.optJSONObject("artwork");art.load(a==null?"":a.optString("thumbnail"),image);setContentDescription(value.optString("title")+I18n.t("，",", ")+host.availability(value));return true;}
    }
}
