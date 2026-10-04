package com.cometinha.touchdrop;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.nfc.*;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int PICK=10;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private NfcAdapter nfc;
    private TextView status, hint;
    private ProgressBar progress;
    private ImageView preview;
    private Button choose, send, receive, cancel;
    private RippleView ripple;
    private File photo;
    private volatile boolean receiving, armed, active;
    private final AtomicBoolean sending=new AtomicBoolean();
    private volatile long epoch;
    private volatile ServerSocket listener;
    private volatile Socket connection;
    private AlertDialog consent;
    private long lastProgress;
    private final ExecutorService io=Executors.newCachedThreadPool();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); nfc=NfcAdapter.getDefaultAdapter(this); buildUi();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
    private Button button(String value,LinearLayout box,Runnable action){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(67,48,108));bg.setCornerRadius(dp(18));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(10);box.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(17,16,26));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(20),dp(24),dp(20));
        scroll.addView(box);setContentView(scroll);
        // Android 15 enforces edge-to-edge for target 35; keep controls outside system bars.
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());box.setPadding(dp(24)+bars.left,dp(20)+bars.top,dp(24)+bars.right,dp(20)+bars.bottom);}
            else box.setPadding(dp(24)+insets.getSystemWindowInsetLeft(),dp(20)+insets.getSystemWindowInsetTop(),dp(24)+insets.getSystemWindowInsetRight(),dp(20)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        box.addView(text("TouchDrop",34,Color.WHITE));box.addView(text("Encostou. Conectou. Enviou.",14,0xFFB59BFF));
        ripple=new RippleView(this);box.addView(ripple,new LinearLayout.LayoutParams(-1,dp(145)));
        status=text("Vamos enviar uma foto?",20,Color.WHITE);box.addView(status);
        hint=text("Teste 0.1 • NFC + mesmo Wi-Fi/hotspot\nDeixe os dois aparelhos desbloqueados e o app aberto.",14,0xFFB9B3C9);hint.setPadding(0,dp(12),0,dp(12));box.addView(hint);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);preview.setVisibility(View.GONE);box.addView(preview,new LinearLayout.LayoutParams(-1,dp(150)));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);box.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        choose=button("Escolher uma foto",box,()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,PICK);});
        send=button("Enviar por encostar",box,this::armSender);send.setEnabled(false);
        receive=button("Receber foto",box,this::startReceiver);
        cancel=button("Cancelar",box,()->{stopSession();status.setText("Cancelado. Pode tentar de novo.");});cancel.setVisibility(View.GONE);
    }
    private boolean checkNfc(boolean hce){
        if(nfc==null){status.setText("Este aparelho não tem NFC.");return false;}
        if(!nfc.isEnabled()){status.setText("Ative o NFC nas configurações e tente de novo.");startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));return false;}
        if(hce&&!getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)){status.setText("Este aparelho não suporta receber pelo toque NFC.");return false;}
        return true;
    }
    private void controls(boolean busy){active=busy;choose.setEnabled(!busy);receive.setEnabled(!busy);send.setEnabled(!busy&&photo!=null);cancel.setVisibility(busy?View.VISIBLE:View.GONE);}
    private void post(long e,Runnable action){handler.post(()->{if(epoch==e&&!isFinishing()&&!isDestroyed())action.run();});}
    private void touched(){
        ripple.play();status.setText("Toque detectado! Pode separar os celulares.");
        Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(120,VibrationEffect.DEFAULT_AMPLITUDE));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
        stopSession();final long e=epoch;Uri uri=data.getData();controls(true);status.setText("Preparando a foto…");
        io.execute(()->{
            File prepared=null;
            try {
                prepared=File.createTempFile("sending-",".photo",getCacheDir());long size=0;
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(prepared)){
                    if(in==null)throw new IOException("Não foi possível abrir esta foto.");byte[] b=new byte[32768];int n;
                    while((n=in.read(b))!=-1){size+=n;if(size>Wire.MAX_BYTES)throw new IOException("Escolha uma foto de até 25 MB.");if(epoch!=e)throw new IOException("Cancelado.");out.write(b,0,n);}
                }
                Wire.checkSize(size);imageInfo(prepared);
                final File ready=prepared;
                handler.post(()->{if(epoch!=e||isDestroyed()){ready.delete();return;}if(photo!=null)photo.delete();photo=ready;
                    preview.setImageBitmap(thumbnail(photo));preview.setVisibility(View.VISIBLE);
                    status.setText("Foto pronta • "+String.format(Locale.ROOT,"%.1f MB",ready.length()/1048576.0));controls(false);
                });
            }catch(Exception ex){if(prepared!=null)prepared.delete();post(e,()->{status.setText(message(ex));controls(false);});}
        });
    }
    private void armSender(){
        if(photo==null||!checkNfc(false))return;stopSession();armed=true;controls(true);status.setText("Encoste no celular que está recebendo");hint.setText("No outro aparelho, toque em Receber foto.\nAproxime as traseiras por um instante.");enableReader();
    }
    private void enableReader(){
        if(!armed||nfc==null)return;
        try{nfc.enableReaderMode(this,this::onTag,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK|NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,null);}
        catch(Exception ex){stopSession();status.setText("Não foi possível iniciar o NFC. "+message(ex));}
    }
    private void onTag(Tag tag){
        if(!armed||!sending.compareAndSet(false,true))return;
        final long e=epoch; final File selected=photo;
        io.execute(()->{
            try(IsoDep dep=IsoDep.get(tag)){
                if(dep==null)throw new IOException("O outro celular não está no modo Receber foto.");dep.connect();dep.setTimeout(5000);
                byte[] response=dep.transceive(TouchSession.SELECT);requireSuccess(response);
                JSONObject info=new JSONObject(new String(Arrays.copyOf(response,response.length-2),java.nio.charset.StandardCharsets.UTF_8));
                if(info.getInt("v")!=1)throw new IOException("Versão incompatível do TouchDrop.");
                String token=info.getString("t");String ip=info.getString("ip");int port=info.getInt("p");
                if(!token.matches("[a-f0-9]{32}")||!ip.matches("[0-9.]{7,15}")||port<1024||port>65535)throw new IOException("Conexão NFC inválida.");
                InetAddress addr=InetAddress.getByName(ip);if(!(addr instanceof Inet4Address)||!addr.isSiteLocalAddress())throw new IOException("Conecte os dois ao mesmo Wi-Fi ou hotspot.");
                requireSuccess(dep.transceive(TouchSession.ACK));if(epoch!=e)return;post(e,this::touched);
                sendPhoto(e,selected,ip,port,token);
            }catch(Exception ex){post(e,()->{stopSession();status.setText("Falha no envio: "+message(ex));});}
            finally{if(epoch==e)sending.set(false);}
        });
    }
    private static void requireSuccess(byte[] r)throws IOException{if(r==null||r.length<2||r[r.length-2]!=(byte)0x90||r[r.length-1]!=0)throw new IOException("Abra Receber foto no outro celular e encoste novamente.");}
    private void sendPhoto(long e,File selected,String ip,int port,String token)throws Exception{
        if(selected==null||!selected.exists())throw new IOException("Escolha a foto novamente.");
        String hash=Wire.hash(selected);try(Socket socket=new Socket()){
            if(epoch!=e)return;connection=socket;if(epoch!=e){socket.close();return;}socket.connect(new InetSocketAddress(ip,port),10000);socket.setSoTimeout(75000);
            DataOutputStream out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));DataInputStream in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeInt(Wire.MAGIC);out.writeUTF(token);out.writeLong(selected.length());out.writeUTF(hash);out.flush();
            post(e,()->status.setText("Aguardando confirmação no outro celular…"));
            if(in.readByte()!=1)throw new IOException("A foto foi recusada ou a confirmação expirou.");
            post(e,()->{progress.setVisibility(View.VISIBLE);status.setText("Enviando a foto…");});
            try(InputStream file=new FileInputStream(selected)){Wire.copyExact(file,out,selected.length(),(a,b)->showProgress(e,"Enviando",a,b));}out.flush();
            socket.setSoTimeout(30000);if(in.readByte()!=2)throw new IOException("O receptor não confirmou o salvamento.");
            post(e,()->{stopSession();progress.setProgress(100);progress.setVisibility(View.VISIBLE);status.setText("Foto enviada e salva no outro celular!");ripple.play();});
        }finally{if(epoch==e)connection=null;}
    }
    private void startReceiver(){
        if(!checkNfc(true))return;stopSession();final long e=epoch;controls(true);status.setText("Preparando para receber…");
        io.execute(()->{
            try {
                String ip=localIp();ServerSocket server=new ServerSocket(0);
                if(epoch!=e){server.close();return;}listener=server;
                byte[] nonce=new byte[16];new SecureRandom().nextBytes(nonce);String token=Wire.hex(nonce);
                JSONObject info=new JSONObject();info.put("v",1);info.put("ip",ip);info.put("p",server.getLocalPort());info.put("t",token);
                byte[] payload=info.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                post(e,()->{receiving=true;TouchSession.payload=payload;TouchSession.onTouch=()->post(e,this::touched);TouchSession.enabled=true;
                    try{CardEmulation.getInstance(nfc).setPreferredService(this,new ComponentName(this,TouchHceService.class));}
                    catch(Exception ex){stopSession();status.setText("Não foi possível iniciar a recepção NFC. Tente novamente.");return;}
                    status.setText("Pronto para receber pelo toque");hint.setText("No outro celular, escolha a foto e toque em Enviar por encostar.\nAproxime as traseiras. Mesma rede Wi-Fi/hotspot.");});
                while(epoch==e&&!server.isClosed()){
                    try(Socket socket=server.accept()){
                        if(epoch!=e)break;connection=socket;socket.setSoTimeout(10000);
                        receivePhoto(e,socket,token);
                    }catch(Exception ex){if(epoch==e)post(e,()->status.setText("Não recebido: "+message(ex)+"\nPode encostar novamente."));}
                    finally{if(epoch==e)connection=null;}
                }
            }catch(Exception ex){post(e,()->{stopSession();status.setText(message(ex));});}
        });
    }
    private static String localIp()throws Exception{
        List<NetworkInterface> all=Collections.list(NetworkInterface.getNetworkInterfaces());
        all.sort(Comparator.comparingInt(n->n.getName().startsWith("wlan")||n.getName().startsWith("swlan")||n.getName().startsWith("ap")?0:1));
        for(NetworkInterface n:all){
            if(!n.isUp()||n.isLoopback()||n.getName().startsWith("rmnet")||n.getName().startsWith("tun"))continue;
            for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&a.isSiteLocalAddress())return a.getHostAddress();
        }throw new IOException("Conecte ao Wi-Fi ou ligue o hotspot antes de receber.");
    }
    private void receivePhoto(long e,Socket socket,String token)throws Exception{
        DataInputStream in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));DataOutputStream out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        if(in.readInt()!=Wire.MAGIC||!Wire.tokenMatches(in.readUTF(),token))throw new IOException("Conexão não reconhecida. Encoste novamente.");
        long size=in.readLong();Wire.checkSize(size);String hash=in.readUTF();if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Foto inválida.");
        CountDownLatch latch=new CountDownLatch(1);AtomicBoolean accepted=new AtomicBoolean();
        post(e,()->{
            consent=new AlertDialog.Builder(this).setTitle("Receber uma foto?").setMessage(String.format(Locale.ROOT,"%.1f MB • aparelho conectado pelo toque NFC",size/1048576.0))
                .setPositiveButton("Receber",(d,w)->{accepted.set(true);latch.countDown();}).setNegativeButton("Recusar",(d,w)->latch.countDown()).setOnCancelListener(d->latch.countDown()).create();
            consent.show();handler.postDelayed(()->{if(latch.getCount()>0){latch.countDown();if(consent!=null)consent.dismiss();}},60000);
        });
        boolean answered=latch.await(60,TimeUnit.SECONDS);post(e,()->{if(consent!=null){consent.dismiss();consent=null;}});
        if(epoch!=e)return;
        if(!answered||!accepted.get()){out.writeByte(0);out.flush();post(e,()->status.setText("Foto recusada. Pode receber outra."));return;}
        out.writeByte(1);out.flush();socket.setSoTimeout(30000);File temp=File.createTempFile("received-",".photo",getCacheDir());
        try {
            post(e,()->{status.setText("Recebendo a foto…");progress.setVisibility(View.VISIBLE);});
            Wire.receive(in,temp,size,hash,(a,b)->showProgress(e,"Recebendo",a,b));String mime=imageInfo(temp);
            if(epoch!=e)throw new IOException("Cancelado.");savePhoto(temp,mime,e);android.graphics.Bitmap thumb=thumbnail(temp);
            out.writeByte(2);out.flush();
            post(e,()->{stopSession();progress.setVisibility(View.VISIBLE);progress.setProgress(100);status.setText("Foto salva na galeria!");hint.setText("Pictures/TouchDrop");preview.setImageBitmap(thumb);preview.setVisibility(View.VISIBLE);ripple.play();});
        }finally{temp.delete();}
    }
    private static android.graphics.Bitmap thumbnail(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1024||o.outHeight/o.inSampleSize>1024)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;return BitmapFactory.decodeFile(f.getPath(),o);
    }
    private static String imageInfo(File f)throws IOException{
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),opt);
        if(opt.outWidth<=0||opt.outHeight<=0||opt.outWidth>20000||opt.outHeight>20000||((long)opt.outWidth*opt.outHeight)>100000000)throw new IOException("Formato de foto inválido ou imagem grande demais.");
        if(!Arrays.asList("image/jpeg","image/png","image/webp","image/gif","image/heif","image/heic").contains(opt.outMimeType))throw new IOException("Use uma foto JPG, PNG, WebP, GIF ou HEIC.");return opt.outMimeType;
    }
    private Uri savePhoto(File file,String mime,long e)throws IOException{
        String ext=mime.equals("image/jpeg")?"jpg":mime.equals("image/png")?"png":mime.equals("image/webp")?"webp":mime.equals("image/gif")?"gif":"heic";
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"TouchDrop_"+System.currentTimeMillis()+"."+ext);values.put(MediaStore.Images.Media.MIME_TYPE,mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/TouchDrop");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Não foi possível salvar na galeria.");
        boolean ok=false;
        try {
            try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new IOException("Não foi possível gravar a foto.");Wire.copyExact(in,out,file.length(),null);
            }
            if(epoch!=e)throw new IOException("Cancelado.");ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);
            if(getContentResolver().update(uri,ready,null,null)<1)throw new IOException("Não foi possível finalizar a foto.");ok=true;return uri;
        }finally{if(!ok)getContentResolver().delete(uri,null,null);}
    }
    private void showProgress(long e,String verb,long a,long b){long now=SystemClock.elapsedRealtime();if(a!=b&&now-lastProgress<120)return;lastProgress=now;post(e,()->{int p=(int)(a*100/b);progress.setProgress(p);status.setText(verb+" a foto • "+p+"%");});}
    private String message(Exception ex){if(ex instanceof SocketTimeoutException)return "A conexão demorou demais. Confira a rede e tente novamente.";if(ex instanceof ConnectException||ex instanceof NoRouteToHostException)return "Os celulares precisam estar no mesmo Wi-Fi/hotspot, sem isolamento entre aparelhos.";return ex.getMessage()==null?"Tente novamente com os dois apps abertos.":ex.getMessage();}
    private void stopSession(){
        epoch++;armed=false;receiving=false;sending.set(false);TouchSession.clear();
        if(nfc!=null){try{nfc.disableReaderMode(this);CardEmulation.getInstance(nfc).unsetPreferredService(this);}catch(Exception ignored){}}
        ServerSocket s=listener;listener=null;if(s!=null)try{s.close();}catch(IOException ignored){}
        Socket c=connection;connection=null;if(c!=null)try{c.close();}catch(IOException ignored){}
        if(consent!=null){consent.cancel();consent=null;}
        ripple.stop();controls(false);progress.setVisibility(View.GONE);lastProgress=0;
    }
    @Override protected void onResume(){super.onResume();if(armed)enableReader();}
    @Override protected void onPause(){
        super.onPause();if(active||armed||receiving||listener!=null||connection!=null){stopSession();status.setText("Teste pausado. Deixe o app aberto para enviar ou receber.");}
    }
    @Override protected void onDestroy(){stopSession();io.shutdownNow();if(photo!=null)photo.delete();super.onDestroy();}
}
