package com.allianzes.peoplbrain.application;

import android.content.Intent;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.allianzes.picker.PickerActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClickPhoto(View v){
        Intent intent = new Intent(this, net.sourceforge.opencamera.MainActivity.class);
        intent.putExtra("photo",true);
        intent.putExtra("video",false);
        startActivityForResult(intent, 555);
    }
    public void onClickVideo(View v){
        Intent intent = new Intent(this, net.sourceforge.opencamera.MainActivity.class);
        intent.putExtra("photo",false);
        intent.putExtra("video",true);
        startActivityForResult(intent, 555);
    }
    public void onClickPhotoVideo(View v){
        Intent intent = new Intent(this, net.sourceforge.opencamera.MainActivity.class);
        intent.putExtra("photo",true);
        intent.putExtra("video",true);
        startActivityForResult(intent, 555);
    }


    public void onClickPickerPhoto(View v){
        Intent intent = new Intent(this, PickerActivity.class);
        intent.putExtra(PickerActivity.PICKER_MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE);
        startActivityForResult(intent, 666);
    }
    public void onClickPickerVideo(View v){
        Intent intent = new Intent(this, PickerActivity.class);
        intent.putExtra(PickerActivity.PICKER_MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO);
        startActivityForResult(intent, 666);
    }
    public void onClickPickerPhotoVideo(View v){
        Intent intent = new Intent(this, PickerActivity.class);
        startActivityForResult(intent, 666);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println( "REQ: "+ requestCode );
        System.out.println( "RES: "+resultCode );
        System.out.println( "DAT: "+data );
    }
}
