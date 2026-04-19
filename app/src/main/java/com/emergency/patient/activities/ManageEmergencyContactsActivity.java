package com.emergency.patient.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.EmergencyContactEntity;
import com.emergency.patient.security.TokenManager;

import java.util.ArrayList;
import java.util.List;

public class ManageEmergencyContactsActivity extends AppCompatActivity {

    private RecyclerView rvContacts;
    private View llEmptyState;
    private TextView tvCount;
    private ContactAdapter adapter;
    private List<EmergencyContactEntity> contactList = new ArrayList<>();
    private AppDatabase db;
    private String patientUuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_emergency_contacts);

        db = AppDatabaseProvider.getInstance(this);
        patientUuid = TokenManager.getUUID(this);

        rvContacts = findViewById(R.id.rv_contacts);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvCount = findViewById(R.id.tv_contact_count);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_contact).setOnClickListener(v -> showAddContactDialog());

        setupRecyclerView();
        loadContacts();
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter();
        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        rvContacts.setAdapter(adapter);
    }

    private void loadContacts() {
        new Thread(() -> {
            List<EmergencyContactEntity> contacts = db.emergencyContactDao().getContactsForPatient(patientUuid);
            runOnUiThread(() -> {
                contactList.clear();
                if (contacts != null) {
                    contactList.addAll(contacts);
                }
                updateUI();
            });
        }).start();
    }

    private void updateUI() {
        adapter.notifyDataSetChanged();
        llEmptyState.setVisibility(contactList.isEmpty() ? View.VISIBLE : View.GONE);
        tvCount.setText(contactList.size() + "/5");
        
        // Disable add button if limit reached
        findViewById(R.id.btn_add_contact).setEnabled(contactList.size() < 5);
        findViewById(R.id.btn_add_contact).setAlpha(contactList.size() < 5 ? 1.0f : 0.5f);
    }

    private void showAddContactDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        EditText etName = view.findViewById(R.id.et_dialog_name);
        EditText etPhone = view.findViewById(R.id.et_dialog_phone);

        new AlertDialog.Builder(this)
                .setTitle("Add Emergency Contact")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();

                    if (name.isEmpty() || phone.isEmpty()) {
                        Toast.makeText(this, "Please enter both name and phone", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!phone.matches("\\d{10}")) {
                        Toast.makeText(this, "Phone must be 10 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveContact(name, phone);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveContact(String name, String phone) {
        EmergencyContactEntity contact = new EmergencyContactEntity(patientUuid, name, phone);

        new Thread(() -> {
            db.emergencyContactDao().insert(contact);
            runOnUiThread(this::loadContacts);
        }).start();
    }

    private void deleteContact(EmergencyContactEntity contact) {
        new Thread(() -> {
            db.emergencyContactDao().delete(contact);
            runOnUiThread(this::loadContacts);
        }).start();
    }

    class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_manage, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EmergencyContactEntity contact = contactList.get(position);
            holder.tvName.setText(contact.name);
            holder.tvPhone.setText(contact.phoneNumber);
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(ManageEmergencyContactsActivity.this)
                        .setTitle("Delete Contact")
                        .setMessage("Are you sure you want to remove " + contact.name + "?")
                        .setPositiveButton("Remove", (dialog, which) -> deleteContact(contact))
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return contactList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone;
            ImageButton btnDelete;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_contact_name);
                tvPhone = itemView.findViewById(R.id.tv_contact_phone);
                btnDelete = itemView.findViewById(R.id.btn_delete_contact);
            }
        }
    }
}
