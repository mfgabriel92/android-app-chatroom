package com.example.gabriel.chatroom;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private ListView mListMessage;
    private MessageAdapter mMessageAdapter;
    private ImageButton mImgPhotoPickerBtn;
    private EditText mEtMessage;
    private ImageButton mImgBtnSend;
    private ProgressBar mPbLoading;
    private String mUsername;

    private static final String ANONYMOUS = "anonymous";
    private static final String MESSAGES_REFERENCE = "messages";
    private static final String CHAT_PHOTOS_REFERENCE = "chat_photos";
    private static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mPhotoStorageReference;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        setupEditTextListener();
        setupAuthStateChanged();
        setupFirebaseRemoteConfig();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (requestCode == RESULT_OK) {
                Toast.makeText(this, "Signed in", Toast.LENGTH_SHORT).show();
            } else {
                finish();
            }
        }

        if (requestCode == RC_PHOTO_PICKER) {
            if (data != null) {
                Toast.makeText(MainActivity.this, "Uploading...", Toast.LENGTH_SHORT).show();

                Uri file = data.getData();
                final StorageReference reference = mPhotoStorageReference.child(file.getLastPathSegment());
                UploadTask upload = reference.putFile(file);

                upload.addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Image could not be uploaded: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

                upload.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                    @Override
                    public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }

                        return reference.getDownloadUrl();
                    }
                }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                        if (task.isSuccessful()) {
                            Uri downloadUrl = task.getResult();
                            ChatroomMessage message = new ChatroomMessage(null, mUsername, downloadUrl.toString());

                            mMessagesDatabaseReference.push().setValue(message);

                            Toast.makeText(MainActivity.this, "Image uploaded!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, task.getException().toString(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        detachDatabaseListener();
        mMessageAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_signout:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Executed when the {@link ImageButton} in {@link com.example.gabriel.chatroom.R.layout#activity_main} is clicked
     * @param view
     */
    public void onPhotoPickerClick(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.complete_action_using)),
            RC_PHOTO_PICKER
        );
    }

    /**
     * Executed when the {@link Button} in {@link com.example.gabriel.chatroom.R.layout#activity_main} is clicked
     * @param view View
     */
    public void onSendClick(View view) {
        ChatroomMessage message = new ChatroomMessage(mEtMessage.getText().toString(), mUsername, null);
        mMessagesDatabaseReference.push().setValue(message);

        mEtMessage.setText("");
    }

    /**
     * Method to separate the instantiation and initialization of variables.
     */
    private void init() {
        mUsername = ANONYMOUS;

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child(MESSAGES_REFERENCE);
        mPhotoStorageReference = mFirebaseStorage.getReference().child(CHAT_PHOTOS_REFERENCE);

        mListMessage = findViewById(R.id.list_message);
        mImgPhotoPickerBtn = findViewById(R.id.img_photo_picker_btn);
        mEtMessage = findViewById(R.id.et_message);
        mImgBtnSend = findViewById(R.id.btn_send);
        mPbLoading = findViewById(R.id.pb_loading);

        List<ChatroomMessage> chatroomMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, chatroomMessages);
        mListMessage.setAdapter(mMessageAdapter);

        mPbLoading.setVisibility(ProgressBar.INVISIBLE);
    }

    /**
     * Add addTextChangedListener to mEtMessage variable
     */
    private void setupEditTextListener() {
        mEtMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() > 0) {
                    mImgBtnSend.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_button_send));
                    mImgBtnSend.setClickable(true);
                } else {
                    mImgBtnSend.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_button_send_disabled));
                    mImgBtnSend.setClickable(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mEtMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});
    }

    /**
     * Attach the read permission
     */
    private void attachDatabaseListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    ChatroomMessage messages = dataSnapshot.getValue(ChatroomMessage.class);
                    mMessageAdapter.add(messages);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    /**
     * Detach the read permissions
     */
    private void detachDatabaseListener() {
        if (mChildEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    /**
     * Initializes the authentication state listener
     */
    private void setupAuthStateChanged() {
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {
                    onSignedIn(user.getDisplayName());
                } else {
                    onSignedOut();
                    startActivityForResult(
                        AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(Arrays.asList(
                                new AuthUI.IdpConfig.EmailBuilder().build(),
                                new AuthUI.IdpConfig.GoogleBuilder().build()
                            ))
                            .build(),
                        RC_SIGN_IN);
                }
            }
        };
    }

    /**
     * Apply the Remote Config set up in Firebase console
     */
    private void setupFirebaseRemoteConfig() {
        FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder()
            .setDeveloperModeEnabled(BuildConfig.DEBUG)
            .build();

        mFirebaseRemoteConfig.setConfigSettings(settings);

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("message_length", DEFAULT_MSG_LENGTH_LIMIT);

        mFirebaseRemoteConfig.setDefaults(defaultConfig);

        long expiration = 3600;

        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            expiration = 0;
        }

        mFirebaseRemoteConfig.fetch(expiration).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFirebaseRemoteConfig.activateFetched();

                Long message_length = mFirebaseRemoteConfig.getLong("message_length");
                mEtMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(message_length.intValue())});
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {

            }
        });
    }

    /**
     * Executed when the user signs in. It will give a username and read permissions
     * @param displayName String the username that will be displayed along with the messages
     */
    private void onSignedIn(String displayName) {
        mUsername = displayName;
        mMessageAdapter.setUsername(mUsername);
        attachDatabaseListener();
    }

    /**
     * Executed when the user signs out. It will erase its username, clear the messages and revoke
     * the read permissions
     */
    private void onSignedOut() {
        mUsername = ANONYMOUS;
        mMessageAdapter.setUsername(mUsername);
        mMessageAdapter.clear();
        detachDatabaseListener();
    }
}
