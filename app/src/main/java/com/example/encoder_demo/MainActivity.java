package com.example.encoder_demo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Inherited;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.MainThreadDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;



/*
*
* Have created temporary file in cache directory and ffmpeg is able to read that temp directory file.
* What remains is to encode, and transfer that temp directory file back to the sd card location or whereever you want to. Also clear out cache directory after transfer.
 */

public class MainActivity extends AppCompatActivity {

    AlertDialog copyDialog;
    AlertDialog.Builder builder;
    AlertDialog encodingDialog;
    EditText startTime;
    EditText endTime;
    int partsCount;
    int currentPartNumber = 0;

    private int UPDATE_PROGRESS=1;
    int t=0; // the value for parameter -t in ffmpeg

    TextView encodeProgressPercent, currentPart;

    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startTime = findViewById(R.id.startStamp);
        endTime = findViewById(R.id.endStamp);
        //if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},1);

        //if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},1);

        handler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                if(msg.what == UPDATE_PROGRESS){
                    Log.d("handler",""+msg.arg1);
                    encodeProgressPercent.setText(msg.arg1 + "%");
                    currentPart.setText("" + msg.arg2 + "/" + partsCount);
                }
            }
        };

    }
    @Override
    protected void onActivityResult(int request_code,int result_code, Intent data)
    {
        super.onActivityResult(request_code,result_code,data);
        Log.d("SAF","request_code = " + request_code + " result_code = " + result_code);

        switch (request_code)
        {
            case 1:
                if(result_code== Activity.RESULT_OK)
                {
                    Log.d("SAF",data.getData().toString());
                    Log.d("Stored_permissions",""+getContentResolver().getPersistedUriPermissions().size());
                    permission_ok(data.getData());
                }
                break;
            case 2:
                if(result_code == Activity.RESULT_OK) {
                    int takeFlags = data.getFlags()
                            &
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    this.getContentResolver().takePersistableUriPermission(data.getData(),takeFlags);
                    SharedPreferences sharedPref = this.getSharedPreferences("com.example.encoder_demo",Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("uri",data.getData().toString());
                    editor.commit();
                    Log.d("SAF",data.getData().toString());
                    copyToDirectory(data.getData());
                }
        }
    }

    private void setCopyingDialog(){
        builder = new AlertDialog.Builder(this);
        builder.setTitle("Wait..");
        final View CopyLayout = getLayoutInflater().inflate(R.layout.copy_dialog_layout, null);
        builder.setView(CopyLayout);
        copyDialog = builder.create();
    }

    private void copyToCache(DocumentFile df,File cacheOGFile){
        try {
            cacheOGFile.createNewFile(); // Creating a placeholder empty file to which data will be copied and written below
            InputStream is = getContentResolver().openInputStream(df.getUri());

            OutputStream os = getContentResolver().openOutputStream(Uri.fromFile(cacheOGFile));
            byte[] buffer = new byte[2048];
            int read;
            while ((read = is.read(buffer)) != -1) {

                os.write(buffer, 0, read);
            }
            os.close();
            File cacheOGFile1=getApplicationContext().getCacheDir();
            File[] filelist = cacheOGFile1.listFiles();
            Log.d("File","Total files in cache = " + filelist.length);
            for(int i = 0 ; i < filelist.length ; i++)
            {
                Log.d("File"," Name = " +  filelist[i].getName());
            }
        }
        catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    private void copyToDirectory(Uri path) {
        DocumentFile df = DocumentFile.fromTreeUri(this,path);
        Log.d("outputfolder","Read Stat = " + df.canRead() + " location = "+df.getUri().toString());

        File[] files = new File(getApplicationContext().getCacheDir().getPath() + "/").listFiles();

        for(int i = 0 ; i < files.length ; i++) {
            try {
                InputStream is = getContentResolver().openInputStream(Uri.fromFile(files[i]));
                DocumentFile temp = df.createFile("video/mp4", files[i].getName());
                OutputStream os = getContentResolver().openOutputStream(temp.getUri());

                byte[] buffer = new byte[2048];
                int read;
                while ((read = is.read(buffer)) != -1) {

                    os.write(buffer, 0, read);
                }
                os.close();
                is.close();
                files[i].delete();
            }
            catch (IOException ioe){
                ioe.printStackTrace();
            }
        }

    }

    private void setProcessinDialog(){
        builder.setTitle("Encoding..");
        final View encodingLayout = getLayoutInflater().inflate(R.layout.encoding_dialog, null);
        encodeProgressPercent =  encodingLayout.findViewById(R.id.encodingProgress);
        currentPart = encodingLayout.findViewById(R.id.partNumber);
        builder.setView(encodingLayout);
        encodingDialog = builder.create();
    }

    private int encode(File cacheOGFile){
        MediaInformation mediaInfo = FFprobe.getMediaInformation(cacheOGFile.getPath());
        Log.d("Ffmpeg","Media duration = " + mediaInfo.getDuration());

        String[] startSplitString= startTime.getText().toString().split(":");
        String[] endSplitString= endTime.getText().toString().split(":");
        int[] startStamps = new int[3];
        int[] endStamps = new int[3];

        if( startSplitString.length != 3 || endSplitString.length != 3 ) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Timestamp format incorrect", Toast.LENGTH_SHORT).show();
                }
            });
            return 1;
        }

        for(int i = 0 ; i < startSplitString.length ; i++)
        {
            startStamps[i] = Integer.parseInt(startSplitString[i]);
            endStamps[i] = Integer.parseInt((endSplitString[i]));
        }

        int totalseconds = (( endStamps[0] - startStamps[0] ) * 3600 ) + ((endStamps[1] - startStamps[1]) * 60) + (endStamps[2] - startStamps[2]);
        Log.d("ffmpeg","Total seconds = " + totalseconds);

        if( totalseconds <= 0) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Invalid timestamps", Toast.LENGTH_SHORT).show();
                }
            });
            return 1;
        }
        Log.d("Videolength",totalseconds + " " + mediaInfo.getDuration());
        /*if(totalseconds*1000 > mediaInfo.getDuration()){
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Timestamps give more value than the video length", Toast.LENGTH_SHORT).show();
                }
            });
            return 1;
        }*/

        partsCount =  (int)Math.ceil((double)totalseconds / 30d);

        String staticParameters = "-i "+ "\"" +getApplicationContext().getCacheDir().getPath() + "/" + cacheOGFile.getName() + "\"" + " -c:v libx264 -crf 19 -pix_fmt yuv420p -filter:v scale=700:-1 -c:a libmp3lame -b:a 128k -maxrate 2.4M -bufsize 3M ";
        //String staticParameters = "-i "+ getApplicationContext().getCacheDir().getPath() + "/" + cacheOGFile.getName() + " -c:v libx264 -pix_fmt yuv420p -filter:v scale=700:-1 -b 1800k -minrate 1800k -maxrate 2300k -c:a libmp3lame -b:a 128k -bufsize 3M ";
        int startSeconds = startStamps[0] * 3600  + startStamps[1] * 60 + startStamps[2];
        int endSeconds = endStamps[0] * 3600 + endStamps[1] * 60 + endStamps[2];

        String opFileName=  cacheOGFile.getName().substring(0,cacheOGFile.getName().length() - 4);

        int ss = startSeconds;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                encodingDialog.show();
            }
        });

        Config.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                Log.d("Statistics","stat time = "  + statistics.getTime() + " t = " + MainActivity.this.t);
                int percent = (int) (( (float)statistics.getTime() / ( (float)MainActivity.this.t * 1000 ) ) * 100);
                Message msg = handler.obtainMessage();
                msg.what=UPDATE_PROGRESS;
                msg.arg1 = percent;
                msg.arg2 = currentPartNumber+1;
                handler.sendMessage(msg);
            }
        });

        for(int i = 0 ; i < partsCount ; i++){
            ss += t;
            if( (endSeconds - ss) > 30) {
                t = 30;
            }
            else {
                t = endSeconds - ss;
            }
            currentPartNumber = i;
            int resultCode = FFmpeg.execute( "-ss " + ss  + " " + staticParameters + " -t " + t + " \"" + getApplicationContext().getCacheDir().getPath() + "/" + opFileName + i + "part.mp4\"");
            Log.d("ffmpeg_execution",staticParameters + opFileName + i + "part.mp4");
            Log.d("ffmpeg_execution","Result code = "+ resultCode + " part = "+i );
        }
        Log.d("ffmpeg","total parts = " + partsCount);

        writeFolderPermission();

        return 0;
    }

    protected void permission_ok(Uri path) {

        final DocumentFile df = DocumentFile.fromSingleUri(this,path);//DocumentFile.fromTreeUri(this, path).findFile("op.mp4");
        final File cacheOGFile = new File(getApplicationContext().getCacheDir().getPath() + "/" + df.getName());
        Log.d("File"," Selected File Name = " + cacheOGFile.getName());
        if (df.isFile() && df.canRead())
        {
            try {
                setCopyingDialog();
                setProcessinDialog();
                copyDialog.show();
                Completable.fromAction( () -> {
                    copyToCache(df,cacheOGFile);
                    Log.d("Thread_stat","Copied to cache");
                })
                        .andThen( Completable.fromAction( () -> {
                            copyDialog.dismiss();
                            Log.d("Thread_stat","Encode started");
                            encode(cacheOGFile);
                            if(cacheOGFile.delete()){ // Delete the original file after encode is done
                                Log.d("File","Cache file deleted");
                            }
                        } ))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe( () -> {
                            encodingDialog.dismiss();
                        } );
                //Toast.makeText(this.getApplicationContext(), "File Copied !", Toast.LENGTH_SHORT).show();
                //Log.d("Path = ","-i "+ getApplicationContext().getCacheDir()+"/"+parameters.getText().toString());

                //int rc = FFmpeg.execute("-i "+ getApplicationContext().getCacheDir()+"/"+parameters.getText().toString());
                //Toast.makeText(this, "rc = " + rc, Toast.LENGTH_SHORT).show();
            }
            catch(Exception e)
            {
                e.getMessage();
                e.printStackTrace();
            }
        }
    }

    public void selectFile(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); //Acts as file picker
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, 1);
    }

    public void writeFolderPermission(){

        SharedPreferences sharedPref = this.getSharedPreferences("com.example.encoder_demo",Context.MODE_PRIVATE);
        String uri = sharedPref.getString("uri",null);
        if(uri != null){
            copyToDirectory(Uri.parse(uri));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent,2);
    }
}
