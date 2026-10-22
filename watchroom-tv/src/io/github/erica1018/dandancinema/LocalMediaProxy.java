package io.github.erica1018.dandancinema;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Old vendor MediaPlayer rejects custom headers. Keep pairing in this app's HTTP client.
 * The local listener exposes exactly one media URL for one playback session, never arbitrary URLs.
 */
final class LocalMediaProxy implements Closeable {
    private final ServerSocket listener;
    private final String upstream, token, path;
    private final boolean hls;
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private volatile boolean closed;
    LocalMediaProxy(String upstream,String token) throws IOException {
        this.upstream=upstream;this.token=token;this.hls=upstream.endsWith("/index.m3u8");this.path="/"+UUID.randomUUID().toString()+(hls?"/index.m3u8":".mp4");
        listener=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
        Thread thread=new Thread(()->{while(!closed){try{Socket s=listener.accept();if(!closed)workers.execute(()->serve(s));else s.close();}catch(Exception ignored){}}},"watchroom-local-player");thread.setDaemon(true);thread.start();
    }
    String url(){return "http://127.0.0.1:"+listener.getLocalPort()+path;}
    private String line(InputStream in) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n')break;if(c!='\r')out.write(c);if(out.size()>4096)throw new IOException();}return out.toString("US-ASCII");}
    private void serve(Socket socket){HttpURLConnection remote=null;
        try(Socket local=socket){local.setSoTimeout(15000);InputStream input=local.getInputStream();OutputStream output=local.getOutputStream();String[] request=line(input).split(" ");
            if(request.length!=3||!("GET".equals(request[0])||"HEAD".equals(request[0])))return;
            String target=upstream;
            if(hls){String prefix=path.substring(0,path.lastIndexOf('/')+1);if(!request[1].startsWith(prefix))return;String name=request[1].substring(prefix.length());if(!name.matches("index\\.m3u8|segment[0-9]{6}\\.ts"))return;target=upstream.substring(0,upstream.lastIndexOf('/')+1)+name;}
            else if(!path.equals(request[1]))return;
            String range=null;for(int i=0;i<30;i++){String h=line(input);if(h.isEmpty())break;if(h.toLowerCase(Locale.ROOT).startsWith("range:")){range=h.substring(6).trim();if(!range.matches("bytes=[0-9]*-[0-9]*"))return;}if(i==29)return;}
            remote=(HttpURLConnection)new URL(target).openConnection();remote.setConnectTimeout(8000);remote.setReadTimeout(30000);remote.setRequestMethod(request[0]);remote.setInstanceFollowRedirects(false);remote.setRequestProperty("X-Watchroom-Pair",token);if(range!=null)remote.setRequestProperty("Range",range);
            int code=remote.getResponseCode();if(code!=200&&code!=206){output.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));return;}
            String type=hls?(target.endsWith(".m3u8")?"application/vnd.apple.mpegurl":"video/mp2t"):"video/mp4";
            StringBuilder headers=new StringBuilder("HTTP/1.1 "+code+(code==206?" Partial Content":" OK")+"\r\nConnection: close\r\nContent-Type: "+type+"\r\nAccept-Ranges: bytes\r\n");
            for(String key:new String[]{"Content-Length","Content-Range"}){String value=remote.getHeaderField(key);if(value!=null&&!value.contains("\r")&&!value.contains("\n"))headers.append(key).append(": ").append(value).append("\r\n");}headers.append("\r\n");output.write(headers.toString().getBytes("US-ASCII"));output.flush();
            if("GET".equals(request[0]))try(InputStream in=remote.getInputStream()){byte[] b=new byte[65536];int n;while(!closed&&(n=in.read(b))!=-1)output.write(b,0,n);}
        }catch(Exception ignored){}finally{if(remote!=null)remote.disconnect();}
    }
    public void close(){closed=true;try{listener.close();}catch(IOException ignored){}workers.shutdownNow();}
}
