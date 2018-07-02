package com.example.user.payme;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.ListView;

import com.example.user.payme.Adapters.ContactAdapter;
import com.example.user.payme.Fragments.AccountSettingsFragment;
import com.example.user.payme.Fragments.ContactsFragment;
import com.example.user.payme.Objects.Contact;

import java.util.ArrayList;

public class ChooseContact extends AppCompatActivity implements ContactsFragment.OnFragmentInteractionListener {
    private static final int readContactPermissionID = 104;
    private ListView listView;
    private ArrayList<Contact> contactsList;
    private ContactAdapter mAdapter;
    private Cursor cursor;

    private Fragment fragment;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final FragmentManager fragmentManager = getSupportFragmentManager();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_contact);

        fragment = new ContactsFragment();

        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container_choose, fragment);
        transaction.commit();

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        BottomNavigationViewHelper.disableShiftMode(navigation);

        navigation.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.navigation_home:
                                break;
                            case R.id.navigation_history:
                                break;
                            case R.id.navigation_addNewReceipt:
                                return true;
                            case R.id.navigation_contacts:
//                                fragment = new ContactsFragment();
                                break;
                            case R.id.navigation_account:
//                                fragment = new AccountSettingsFragment();
                                break;
                        }
//                        final FragmentTransaction transaction = fragmentManager.beginTransaction();
//                        transaction.replace(R.id.fragment_container, fragment);
//                        transaction.commit();
                        return true;
                    }
                });
    }

    @Override
    public void onFragmentInteraction() {

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    public void setActionBarTitle(String title) {
        getSupportActionBar().setTitle(title);
    }
}