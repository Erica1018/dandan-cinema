package io.github.erica1018.dandancinema;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Same paired server only; immutable art paths, bounded image bytes and local cache. */
public final class ArtworkLoader {
    private final String server,token;
    private final File folder;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final HashMap<String,ArrayList<ImageView>> waiting=new HashMap<>();
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};
    private boolean closed;
    public ArtworkLoader(Context context,String server,String token){this.server=server;this.token=token;folder=new File(context.getCacheDir(),"artwork-v1");folder.mkdirs();}
    public void load(String path,ImageView target){
        if(path.equals(target.getTag()))return;target.setTag(path);
        if(!path.matches("/art/[0-9a-f]{64}\\.jpg")||server.isEmpty()){target.setImageDrawable(null);return;}
        Bitmap cached=memory.get(path);if(cached!=null){target.setImageBitmap(cached);return;}
        target.setImageDrawable(null);ArrayList<ImageView> pending=waiting.get(path);if(pending!=null){pending.add(target);return;}
        pending=new ArrayList<>();pending.add(target);waiting.put(path,pending);
        workers.execute(()->{Bitmap loaded=null;HttpURLConnection connection=null;File file=new File(folder,path.substring(5));try{
            byte[] bytes;if(file.isFile()&&file.length()<=1000000){try(FileInputStream input=new FileInputStream(file)){bytes=read(input);}file.setLastModified(System.currentTimeMillis());}
            else{connection=(HttpURLConnection)new URL(server+path).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(4000);connection.setReadTimeout(5000);connection.setRequestProperty("X-Watchroom-Pair",token);if(connection.getResponseCode()!=200)throw new IOException("Art unavailable");try(InputStream input=connection.getInputStream()){bytes=read(input);}File temporary=new File(folder,file.getName()+".tmp");try(FileOutputStream output=new FileOutputStream(temporary)){output.write(bytes);}if(!temporary.renameTo(file))temporary.delete();trim();}
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>1920||bounds.outHeight>1080)throw new IOException("Art dimensions exceed limit");BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;loaded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}final Bitmap bitmap=loaded;main.post(()->{ArrayList<ImageView> targets=waiting.remove(path);if(closed||targets==null)return;if(bitmap!=null)memory.put(path,bitmap);for(ImageView view:targets)if(path.equals(view.getTag())&&bitmap!=null)view.setImageBitmap(bitmap);});});
    }
    private static byte[] read(InputStream input)throws IOException{ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){output.write(buffer,0,n);if(output.size()>1000000)throw new IOException("Art too large");}return output.toByteArray();}
    private synchronized void trim(){File[] files=folder.listFiles((dir,name)->name.matches("[0-9a-f]{64}\\.jpg"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();for(int i=0;i<files.length&&(total>24*1024*1024||files.length-i>48);i++){long length=files[i].length();if(files[i].delete())total-=length;}}
    public void close(){closed=true;workers.shutdownNow();waiting.clear();memory.evictAll();}
}
