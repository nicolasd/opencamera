package com.allianzes.picker;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.allianzes.picker.adapter.MediaAdapter;
import com.allianzes.picker.utils.MediaFileInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Media List
 */
public class PickerActivity extends AppCompatActivity implements MediaAdapter.MediaListRowHolder.ClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private ArrayList<MediaFileInfo> mediaList = new ArrayList<>();
    //private RecyclerView mRecyclerView;
    private MediaAdapter adapter;
    private final String SAVE_INSTANCE_LIST_MEDIA = "SAVE_INSTANCE_LIST_MEDIA";
    private final String SAVE_INSTANCE_LIST_MEDIA_ID_SELECTED = "SAVE_INSTANCE_LIST_MEDIA_ID_SELECTED";
    private static final int REQUEST_MEDIA_PERMISSIONS = 2;
    private static final String FRAGMENT_DIALOG = "permission_dialog";
    private static final String[] MEDIA_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    public final static String PEOPLBRAIN_MEDIA_SELECTED = "com.allianzes.peoplbrain.editor.media.list.selected";
    public final static String PICKER_MEDIA_TYPE = "com.allianzes.picker.media.type";

    private int currentType = 0;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picker_list);

        RecyclerView  mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        if( getIntent() != null && getIntent().hasExtra(PICKER_MEDIA_TYPE)){
            currentType = getIntent().getIntExtra(PICKER_MEDIA_TYPE, 0);
        }

        if( mRecyclerView != null) {
            mRecyclerView.setLayoutManager(new GridLayoutManager(this, getResources().getInteger(R.integer.peoplbrain_editor_video_list_columns)));
            adapter = new MediaAdapter(PickerActivity.this, mediaList, PickerActivity.this);
            mRecyclerView.setAdapter(adapter);
            if (!hasPermissionsGranted(MEDIA_PERMISSIONS)) {
                requestMediaListPermissions();
                return;
            }
            if( savedInstanceState == null ){
                new MediaAsyncTask().execute();
            }else {
                if (savedInstanceState.getSerializable(SAVE_INSTANCE_LIST_MEDIA) != null) {
                    mediaList = (ArrayList<MediaFileInfo>) savedInstanceState.getSerializable(SAVE_INSTANCE_LIST_MEDIA);
                    adapter = new MediaAdapter(PickerActivity.this, mediaList,PickerActivity.this);
                    if(savedInstanceState.getSerializable(SAVE_INSTANCE_LIST_MEDIA_ID_SELECTED) != null)
                        adapter.setSelectedItems((List<Integer>) savedInstanceState.getSerializable(SAVE_INSTANCE_LIST_MEDIA_ID_SELECTED));
                    mRecyclerView.setAdapter(adapter);
                }
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.peoplbrain_editor_menu_medialist, menu);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        if (menu.findItem(R.id.peoplbrain_editor_action_send) != null) {
            MenuItem item_send = menu.findItem(R.id.peoplbrain_editor_action_send);
            if(adapter != null){
                if( adapter.getSelectedItemCount() > 0){
                    item_send.setVisible(true);
                    item_send.setTitle("Envoyer " +adapter.getSelectedItemCount()+ " élément(s)");
                }else {
                    item_send.setVisible(false);
                }
            }else{
                item_send.setVisible(true);
            }
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if(mediaList != null && mediaList.size() > 0) {
            outState.putSerializable(SAVE_INSTANCE_LIST_MEDIA, mediaList);
            outState.putSerializable(SAVE_INSTANCE_LIST_MEDIA_ID_SELECTED,(ArrayList)adapter.getSelectedItems());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.peoplbrain_editor_action_send) {
            Toast.makeText(PickerActivity.this,
                    "Selected Items : \n" + adapter.getSelectedItemCount(), Toast.LENGTH_LONG).show();
            if(adapter != null && adapter.getSelectedItemCount() > 0) {
                ArrayList<String> medias = new ArrayList<>();
                for (int i = 0; i < adapter.getSelectedItems().size(); ++i) {
                    medias.add(mediaList.get(adapter.getSelectedItems().get(i)).getFilePath());
                }
                Intent intent = new Intent();
                intent.putExtra(PEOPLBRAIN_MEDIA_SELECTED, medias);
                setResult(RESULT_OK,intent);
                finish();
            }
        }else if(item.getItemId() == android.R.id.home){
            setResult(RESULT_CANCELED);
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void parseAllMedias() {
        try {
            String[] columns = { MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.TITLE,
            };

            String selection = "";
            if( currentType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ){
                selection += MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
            }else if( currentType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ){
                selection += MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            }else{
                selection += MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
                selection += " OR ";
                selection += MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            }

            Uri queryUri = MediaStore.Files.getContentUri("external");
            Cursor video_cursor = getContentResolver().query(queryUri, columns, selection, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC");

            if( video_cursor != null) {
                int count = video_cursor.getCount();
                for (int i = 0; i < count; i++) {
                    MediaFileInfo mediaFileInfo = new MediaFileInfo();
                    video_cursor.moveToPosition(i);

                    mediaFileInfo.setFileName(video_cursor.getString(video_cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE)));
                    mediaFileInfo.setFilePath(video_cursor.getString(video_cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)));

                    mediaList.add(mediaFileInfo);
                }
                video_cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onItemClicked(int position) {
        adapter.toggleSelection(position);
        invalidateOptionsMenu();
    }

    private class MediaAsyncTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(Void... params) {
            Integer result;
            try {
                parseAllMedias();
                result = 1;

            }catch (Exception e) {
                e.printStackTrace();
                result =0;
            }

            return result; //"Failed to fetch data!";
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 1) {
                if(adapter != null){
                    adapter.notifyDataSetChanged();
                }
            } else {
                Log.e("toto", "Failed to fetch data!");
            }
        }
    }


    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(@SuppressWarnings("SameParameterValue") String[] permissions) {
        for (String permission : permissions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestMediaListPermissions() {
        if (shouldShowRequestPermissionRationale(MEDIA_PERMISSIONS)) {
            new ConfirmationDialog().show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions( MEDIA_PERMISSIONS,REQUEST_MEDIA_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d("PickerActivity", "onRequestPermissionsResult");
        if (requestCode == REQUEST_MEDIA_PERMISSIONS) {
            if (grantResults.length == MEDIA_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance((getResources().getString(R.string.peoplbrain_media_list_permissions_not_granted))).show(getSupportFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
                new MediaAsyncTask().execute();
            } else {
                ErrorDialog.newInstance(("Permission request 2"))
                        .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(@SuppressWarnings("SameParameterValue") String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setMessage(getResources().getString(R.string.peoplbrain_media_list_request_permissions))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                getActivity().requestPermissions( MEDIA_PERMISSIONS,
                                        REQUEST_MEDIA_PERMISSIONS);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getActivity().finish();
                                }
                            })
                    .create();
        }

    }
}
