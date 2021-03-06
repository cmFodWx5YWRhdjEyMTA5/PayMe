package com.example.user.payme;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.user.payme.Objects.User;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class AddReceiptActivity extends AppCompatActivity {

    private static final String TAG = "AddReceiptActivity";
    private static final int REQUEST_CAMERA_ID = 100;
    private static final int WRITE_EXTERNAL_ID = 101;
    private static final int READ_EXTERNAL_ID = 102;

    private SurfaceView mCameraView;
    private CameraSource mCameraSource;
    private ImageButton mTakePicture;
    private ImageButton mGallery;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private byte[] mBytes;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Intent intent = new Intent(AddReceiptActivity.this, MainActivity.class);
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    startActivity(intent);
                    break;
                case R.id.navigation_history:
                    intent.putExtra("startFragment", MainActivity.REQUEST_HISTORY_FRAGMENT);
                    break;
                case R.id.navigation_addNewReceipt:
                    return true;
                case R.id.navigation_contacts:
                    intent.putExtra("startFragment", MainActivity.REQUEST_CONTACTS_FRAGMENT);
                    break;
                case R.id.navigation_account:
                    intent.putExtra("startFragment", MainActivity.REQUEST_ACCOUNT_SETTING_FRAGMENT);
                    break;
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_ID: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        mCameraSource.start(mCameraView.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case WRITE_EXTERNAL_ID: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    SavePhotoToGallery savePhotoToGallery = new SavePhotoToGallery(AddReceiptActivity.this);
                    savePhotoToGallery.execute(mBytes);
                }
                break;
            }
            case READ_EXTERNAL_ID: {
                Log.e(TAG, "onRequestPermissionsResult: COME IN WRONG PLACE READEXTERNAL" );
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    openGallery();
                }
                break;
            }

        }
    }

    private void startCameraSource() {

        //Create the TextRecognizer
        final TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        if (!textRecognizer.isOperational()) {
            Log.w(TAG, "Detector dependencies not loaded yet");
        } else {

            //Initialize camerasource to use high resolution and set Autofocus on.
            mCameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(1280, 1024)
                    .setAutoFocusEnabled(true)
                    .setRequestedFps(2.0f)
                    .build();

            /**
             * Add call back to SurfaceView and check if camera permission is granted.
             * If permission is granted we can start our cameraSource and pass it to surfaceView
             */
            mCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    try {

                        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "surfaceCreated: ASKING CAMERA PERMISSION");
                            ActivityCompat.requestPermissions(AddReceiptActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_ID);
                            return;
                        }

                        mCameraSource.start(mCameraView.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    mCameraSource.stop();
                }
            });

            //Set the TextRecognizer's Processor.
            textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
                @Override
                public void release() {}

                /**
                 * Detect all the text from camera using TextBlock and the values into a stringBuilder
                 * which will then be set to the textView.
                 * */
                @Override
                public void receiveDetections(Detector.Detections<TextBlock> detections) {}
            });
        }
    }

    // Callback for 'takePicture'
    CameraSource.PictureCallback pictureCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] bytes) {
            mBytes = bytes;

            if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                Log.e(TAG, "surfaceCreated: ASKING WRITE EXTERNAL PERMISSION");
                ActivityCompat.requestPermissions(AddReceiptActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_EXTERNAL_ID);
                return;
            }

            SavePhotoToGallery savePhotoToGallery = new SavePhotoToGallery(AddReceiptActivity.this);
            savePhotoToGallery.execute(bytes);
        }
    };

    private void openGallery() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AddReceiptActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_EXTERNAL_ID);
            return;
        }

        Intent gallery = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, READ_EXTERNAL_ID);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == READ_EXTERNAL_ID) {
            Intent intent = new Intent(AddReceiptActivity.this, ChooseContactActivity.class);

            mEditor = mSharedPreferences.edit();
            mEditor.putString("imagePath", data.getData().toString());
            mEditor.commit();

            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manual_appbar, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_manual:
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                Log.d(TAG, "onOptionsItemSelected: default aosdnaksjndkjansd");
                return super.onOptionsItemSelected(item);

        }
    }

    private void getStoreCurrentUser(){
        FirebaseAuth auth;
        FirebaseDatabase db;
        DatabaseReference ref;

        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();
        ref = db.getReference();

        FirebaseUser currentUser = auth.getCurrentUser();
        String userId = currentUser.getUid();

        ref.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                User currentUser = dataSnapshot.getValue(User.class);
                mEditor = mSharedPreferences.edit();
                mEditor.putString("currentUserName",currentUser.getName());
                mEditor.putString("currentUserNumber",currentUser.getNumber());
                mEditor.commit();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_receipt);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbarEdit);
        setSupportActionBar(myToolbar);
        myToolbar.setTitleTextColor(Color.WHITE);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setSelectedItemId(R.id.navigation_addNewReceipt);
        BottomNavigationViewHelper.disableShiftMode(navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mCameraView = (SurfaceView) findViewById(R.id.surfaceView);
        mTakePicture = (ImageButton) findViewById(R.id.cameraButton);
        mGallery = (ImageButton) findViewById(R.id.galleryButton);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mSharedPreferences.edit();
        // fetch and store current user
        if (mSharedPreferences.getString("currentUserName", "").isEmpty()){
            getStoreCurrentUser();
        }

        startCameraSource();
        mTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraSource.takePicture(null, pictureCallback);
            }
        });

        mGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
    }

    private static class SavePhotoToGallery extends AsyncTask<byte[] , Void, String> {
        private final WeakReference<Activity> weakActivity;

        SavePhotoToGallery(Activity weakActivity) {
            this.weakActivity = new WeakReference<>(weakActivity);
        }

        @Override
        protected String doInBackground(byte[]... params) {
            try {
                Log.d(TAG, "doInBackground: START");
                byte[] bytes = params[0];

                String mainpath = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/PayMe";
                File basePath = new File(mainpath);
                if (!basePath.exists()) {
                    Log.d("CAPTURE_BASE_PATH", basePath.mkdirs() ? "Success": "Failed");
                }

                SimpleDateFormat sdf = new SimpleDateFormat("ddMMyy_hhmmss", Locale.ENGLISH);
                String date = sdf.format(new Date());

                File captureFile = new File(mainpath + "/receipt_" + date + ".jpg");
                if (!captureFile.exists()) {
                    Log.d("CAPTURE_FILE_PATH", captureFile.createNewFile() ? "Success": "Failed");
                }

                FileOutputStream stream = new FileOutputStream(captureFile);
                stream.write(bytes);
                stream.flush();
                stream.close();

                Log.d(TAG, "onPictureTaken: imagepath "+captureFile.toString());

                return captureFile.toString();

            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception exception) {
                Log.e(TAG, "doInBackground: ",exception );
                Activity activity = weakActivity.get();
                Toast.makeText(activity.getApplicationContext(),"Error saving: "+ exception.toString(),Toast.LENGTH_LONG).show();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "onPostExecute: DONEEE imagepath "+result);

            if (result != null) {
                Activity activity = weakActivity.get();

                // mSharedPreferences for camera
                SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                        SharedPreferences.Editor mEditor;
                mEditor = mSharedPreferences.edit();
                mEditor.putString("imagePath",result);
                mEditor.apply();

                Intent intent = new Intent(activity, ChooseContactActivity.class);
                activity.startActivity(intent);
            }

        }
    }
}
