package com.example.user.payme.Fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.user.payme.Adapters.HorizontalRecyclerViewAdapter;
import com.example.user.payme.Adapters.VerticalRecyclerViewAdapter;
import com.example.user.payme.ChooseContactActivity;
import com.example.user.payme.Interfaces.ContactClickListener;
import com.example.user.payme.Interfaces.OnFragmentInteractionListener;
import com.example.user.payme.MainActivity;
import com.example.user.payme.Objects.Contact;
import com.example.user.payme.R;
import com.example.user.payme.ShowActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mancj.materialsearchbar.MaterialSearchBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static android.support.v7.widget.DividerItemDecoration.VERTICAL;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class ContactsFragment extends Fragment implements ContactClickListener,  MaterialSearchBar.OnSearchActionListener {

    private static final String TAG = "ContactsFragment";
    private OnFragmentInteractionListener mListener;

    final int PERMISSION_ALL = 1;
    Cursor cursor;
    private Typeface fontFace;
    private Context parentContext;
    private ArrayList<Contact> contactsList;
    private HashMap<String, ArrayList<Contact>> groupList = new HashMap<>();
    private VerticalRecyclerViewAdapter contactsAdapter;
    private Button contactsBtn;
    private Button groupsBtn;
    private View contacts_selected;
    private View groups_selected;
    private MaterialSearchBar searchBar;
    private LinearLayout groupContainer;
    private boolean contactsSelected;

    private FirebaseAuth auth;
    private FirebaseDatabase db;
    private DatabaseReference ref;
    private FirebaseUser currentUser;
    private String userId;

    private ArrayList<Contact> mContacts = new ArrayList<>();
    private Menu mMenu;

    // Empty public constructor, required by the system
    public ContactsFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        parentContext = getContext();
        fontFace = ResourcesCompat.getFont(parentContext, R.font.nunito);
        contactsList = new ArrayList<>();

        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();
        ref = db.getReference();
        currentUser = auth.getCurrentUser();
        userId = currentUser.getUid();

        contactsList = new ArrayList<>();
        setHasOptionsMenu(true);

        // Set title bar
        if (getActivity().getClass().equals(MainActivity.class)) {
            ((MainActivity) getActivity()).setActionBarTitle("Contacts");
        } else {
            ((ChooseContactActivity) getActivity()).setActionBarTitle("Choose Contacts");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        contactsBtn = view.findViewById(R.id.contactsBtn);
        contacts_selected = view.findViewById(R.id.contacts_selected);
        groupsBtn = view.findViewById(R.id.groupsBtn);
        groups_selected = view.findViewById(R.id.groups_selected);
        groupContainer = view.findViewById(R.id.groupContainer);
        searchBar = view.findViewById(R.id.searchBar);

        String[] PERMISSIONS = { Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS };

        listView = view.findViewById(R.id.contactList);
        searchContact = view.findViewById(R.id.searchContact);

        if (!hasPermissions(getActivity(), PERMISSIONS)) { // if permission is not granted
            // Request for permission to read or write to user's contacts
            requestPermissions(PERMISSIONS, PERMISSION_ALL);
        } else {  // if granted
            initEverything();
        }


            GetContactsIntoArrayList();
            mAdapter = new ContactAdapter(getActivity(), contactsList);
            listView.setAdapter(mAdapter);

        return view;
    }

    private void initEverything(){
        GetContactsIntoArrayList();
        GetGroupsIntoHashMap();

        contactsAdapter = new VerticalRecyclerViewAdapter(parentContext, contactsList);

        if (getActivity() instanceof ChooseContactActivity) {
            contactsAdapter = new VerticalRecyclerViewAdapter(parentContext, contactsList, ContactsFragment.this::onContactClick);
        }

        // Show contacts first
        contacts_selected.setVisibility(View.VISIBLE);
        contactsSelected = true;
        ShowContacts();

        contactsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                groups_selected.setVisibility(View.INVISIBLE);
                if (contacts_selected.getVisibility() == View.INVISIBLE) {
                    contacts_selected.setVisibility(View.VISIBLE);
                    contactsSelected = true;
                    searchBar.setPlaceHolder("Search Contacts");
                }
                ShowContacts();
            }
        });

        groupsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                contacts_selected.setVisibility(View.INVISIBLE);
                if (groups_selected.getVisibility() == View.INVISIBLE) {
                    groups_selected.setVisibility(View.VISIBLE);
                    contactsSelected = false;
                    searchBar.setPlaceHolder("Search Groups");
                }
                ShowGroups();
            }
        });

        searchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {  }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (contactsSelected) {  // run this block of code only for search contacts
                    String searchText = searchBar.getText();
                    //Log.d("LOG_TAG", getClass().getSimpleName() + " text changed " + searchText);
                    contactsAdapter.getFilter().filter(searchText);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {  }

        });

        searchBar.setOnSearchActionListener(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.mMenu = menu;
        if (getActivity() instanceof ChooseContactActivity) {
            inflater.inflate(R.menu.choose_contact_bar, menu);
            hideOption(R.id.next);
        } else {
            inflater.inflate(R.menu.contacts_bar, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (getActivity() instanceof ChooseContactActivity) {
            switch (item.getItemId()) {
                case R.id.next:
                    nextShowActvity();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        } else {
            switch (item.getItemId()) {
                case R.id.add_friend:
                    addFriend();
                    return true;
                case R.id.add_group:
                    addGroup();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
    }

    private void hideOption(int id) {
        MenuItem item = mMenu.findItem(id);
        item.setVisible(false);
    }

    private void showOption(int id) {
        MenuItem item = mMenu.findItem(id);
        setHasOptionsMenu(true);
        item.setVisible(true);
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentMessage("ContactsFragment");
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ALL: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    initEverything();
                }

                return;
            }
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void GetContactsIntoArrayList() {

        cursor = getActivity().getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,null, null, null);

        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            Contact contact = new Contact(0, name, phoneNumber);
            if (!contactsList.contains(contact)) {  // to avoid adding duplicates
                contactsList.add(contact);
            }
        }

        Collections.sort(contactsList, (o1, o2) -> o1.getmName().compareTo(o2.getmName()));
        cursor.close();
    }


    public void GetGroupsIntoHashMap() {
        ref.child("users").child(userId).child("groupList")
                .addListenerForSingleValueEvent(new ValueEventListener() {

                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    // Loops through every group
                    for (DataSnapshot groupSnapshot : dataSnapshot.getChildren()) {
                        ArrayList<Contact> contacts = new ArrayList<>();
                        // Loops through every contact in each group
                        for (DataSnapshot contactsSnapshop : groupSnapshot.getChildren()) {
                            Contact c = contactsSnapshop.getValue(Contact.class);
                            contacts.add(c);
                        }
                        groupList.put(groupSnapshot.getKey(), contacts);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {  }
            });
    }


    public void ShowContacts() {
        groupContainer.removeAllViews();
        LinearLayout layout = new LinearLayout(parentContext);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayoutManager layoutManager = new LinearLayoutManager(parentContext, LinearLayout.VERTICAL, false);
        RecyclerView recyclerView = new RecyclerView(parentContext);
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        recyclerView.setAdapter(contactsAdapter);
        DividerItemDecoration decoration = new DividerItemDecoration(parentContext, VERTICAL);
        recyclerView.addItemDecoration(decoration);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setNestedScrollingEnabled(false);
        layout.addView(recyclerView);
        groupContainer.addView(layout);
    }


    public void ShowGroups() {
        groupContainer.removeAllViews();
        Iterator iterator = groupList.entrySet().iterator();
        if (!groupList.isEmpty()) {
            while (iterator.hasNext()) {
                Map.Entry pair = (Map.Entry) iterator.next();
                String groupName = pair.getKey().toString();
                ArrayList<Contact> contacts = (ArrayList<Contact>) pair.getValue();
                LinearLayout layout = new LinearLayout(parentContext);  // create a layout for every group
                layout.setLayoutParams(new LinearLayout.LayoutParams(
                        // int width, int height
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                layout.setPadding(50, 50, 50, 0);
                layout.setOrientation(LinearLayout.VERTICAL);
                TextView nameTextView = new TextView(parentContext);
                nameTextView.setText(groupName);
                nameTextView.setTypeface(fontFace);
                nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

                // TODO
                for (Contact contact: contacts) {
                    Log.d(TAG, "onClick: contact " + contact.getmName() + " selected " + contact.getIsSelected());
                    contact.setSelected(false);
                }

                LinearLayoutManager layoutManager = new LinearLayoutManager(parentContext, LinearLayout.HORIZONTAL, false);
                HorizontalRecyclerViewAdapter adapter = new HorizontalRecyclerViewAdapter(parentContext, contacts);
                RecyclerView recyclerView = new RecyclerView(parentContext);
                recyclerView.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                recyclerView.setAdapter(adapter);
                recyclerView.setLayoutManager(layoutManager);
                View separator = new View(parentContext);
                separator.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3));
                separator.setBackgroundColor(Color.BLACK);
                layout.addView(nameTextView);
                layout.addView(recyclerView);
                layout.addView(separator);
                layout.setTag(groupName);
                groupContainer.addView(layout);

                // On click listener for select group for add receipt
                if (getActivity() instanceof ChooseContactActivity) {
                    layout.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Toast.makeText(parentContext, groupName + " group clicked.", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "onClick: group pass it to showactivity " + groupName);
                            Log.d(TAG, "onClick: list of contacts " + contacts.get(0));
                            for (Contact contact:contacts) {
                                Log.d(TAG, "onClick: contact "+contact.getmName()+" selected "+contact.getIsSelected());
                            }
                            Intent intent = new Intent(getActivity().getBaseContext(), ShowActivity.class);
                            intent.putExtra("from_activity", "ChooseContactActivity");
                            intent.putExtra("Contacts", contacts);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    });
                }
            }
        } else {
            showInfoMessage();
        }

    }

    private void showInfoMessage() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(100, 50, 100, 0);
        TextView messageTextView = new TextView(parentContext);
        messageTextView.setLayoutParams(params);
        //messageTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        messageTextView.setTypeface(fontFace, Typeface.ITALIC);
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        messageTextView.setText(R.string.contacts_info_msg);
        groupContainer.addView(messageTextView);
    }

    private void nextShowActvity() {
        Log.d(TAG, "onContactClick: pass it to showactivity ");
        Intent intent = new Intent(getActivity().getBaseContext(), ShowActivity.class);
        intent.putExtra("from_activity", "ChooseContactActivity");
        intent.putExtra("Contacts", mContacts);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private void addFriend() {
        Fragment friendFragment = new AddFriendFragment();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.fragment_container, friendFragment);
        // previous state will be added to the backstack, allowing you to go back with the back button.
        // must be done before commit.
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void addGroup() {
        Fragment groupFragment = new AddGroupFragment();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.fragment_container, groupFragment);
        // previous state will be added to the backstack, allowing you to go back with the back button.
        // must be done before commit.
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void onContactClick(Contact contact) {
        if (getActivity() instanceof ChooseContactActivity) {
            if (mContacts.contains(contact)) {
                mContacts.remove(contact);
                if (mContacts.size() == 0) {
                    hideOption(R.id.next);
                }
            } else {
                mContacts.add(contact);
                showOption(R.id.next);
            }
        }
    }

    @Override
    public void onSearchStateChanged(boolean enabled) {  }

    @Override
    public void onSearchConfirmed(CharSequence searchText) {
        searchText = searchText.toString().toLowerCase().trim();
        Log.d("LOG_TAG", getClass().getSimpleName() + " text searched. " + searchText);
        for (int i = 0; i < groupContainer.getChildCount(); i++) {
            LinearLayout layout = (LinearLayout) groupContainer.getChildAt(i);
            String tagName = layout.getTag().toString().toLowerCase().trim();
            if (!tagName.contains(searchText)) {
                // if group name does not match the query text, remove the layout
                layout.setVisibility(LinearLayout.GONE);
            }
        }
    }

    @Override
    public void onButtonClicked(int buttonCode) {  }
}
