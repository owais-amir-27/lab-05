package com.example.lab5_starter;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements CityDialogFragment.CityDialogListener {

    private static final String TAG = "MainActivity";

    private Button addCityButton;
    private Button deleteCityButton;
    private ListView cityListView;

    private ArrayList<City> cityArrayList;
    private ArrayAdapter<City> cityArrayAdapter;

    // Firestore
    private FirebaseFirestore db;
    private CollectionReference citiesRef;

    // Tracks the city selected for deletion
    private City selectedCity = null;
    // Tracks the currently highlighted list row view
    private View selectedView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set views
        addCityButton = findViewById(R.id.buttonAddCity);
        deleteCityButton = findViewById(R.id.buttonDeleteCity);
        cityListView = findViewById(R.id.listviewCities);

        // create city array
        cityArrayList = new ArrayList<>();
        cityArrayAdapter = new CityArrayAdapter(this, cityArrayList);
        cityListView.setAdapter(cityArrayAdapter);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        citiesRef = db.collection("cities");

        // Read Data (Real-time)
        citiesRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed", error);
                return;
            }

            if (value != null) {
                cityArrayList.clear();
                for (QueryDocumentSnapshot doc : value) {
                    String name = doc.getString("name");
                    String province = doc.getString("province");

                    if (name == null || province == null) {
                        Log.w(TAG, "Skipping document with missing fields: " + doc.getId());
                        continue;
                    }

                    cityArrayList.add(new City(name, province));
                }
                // Reset selection whenever the list reloads
                selectedCity = null;
                selectedView = null;
                cityArrayAdapter.notifyDataSetChanged();
            }
        });

        // Single tap: select a city for deletion (highlight the row)
        cityListView.setOnItemClickListener((adapterView, view, position, id) -> {
            // Clear highlight on previously selected row
            if (selectedView != null) {
                selectedView.setBackgroundColor(Color.TRANSPARENT);
            }

            selectedCity = cityArrayAdapter.getItem(position);
            selectedView = view;
            view.setBackgroundColor(Color.parseColor("#FFCDD2")); // light red highlight
        });

        // Long click: open the edit dialog
        cityListView.setOnItemLongClickListener((adapterView, view, position, id) -> {
            City city = cityArrayAdapter.getItem(position);
            CityDialogFragment cityDialogFragment = CityDialogFragment.newInstance(city);
            cityDialogFragment.show(getSupportFragmentManager(), "City Details");
            return true;
        });

        // Add City button
        addCityButton.setOnClickListener(view -> {
            CityDialogFragment cityDialogFragment = new CityDialogFragment();
            cityDialogFragment.show(getSupportFragmentManager(), "Add City");
        });

        // Delete button: delete selectedCity from Firestore
        deleteCityButton.setOnClickListener(view -> {
            if (selectedCity == null) {
                Toast.makeText(this, "Please tap a city to select it first", Toast.LENGTH_SHORT).show();
                return;
            }

            String cityName = selectedCity.getName();
            citiesRef.document(cityName)
                    .delete()
                    .addOnSuccessListener(unused -> {
                        Log.d(TAG, "Successfully deleted city: " + cityName);
                        Toast.makeText(this, cityName + " deleted", Toast.LENGTH_SHORT).show();
                        // Reset selection
                        selectedCity = null;
                        if (selectedView != null) {
                            selectedView.setBackgroundColor(Color.TRANSPARENT);
                            selectedView = null;
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete city: " + cityName, e);
                        Toast.makeText(this, "Failed to delete " + cityName, Toast.LENGTH_SHORT).show();
                    });
        });
    }

    @Override
    public void updateCity(City city, String title, String year) {
        city.setName(title);
        city.setProvince(year);
        cityArrayAdapter.notifyDataSetChanged();
    }

    @Override
    public void addCity(City city) {
        cityArrayList.add(city);
        cityArrayAdapter.notifyDataSetChanged();

        if (city.getName() == null || city.getName().trim().isEmpty()) {
            Log.w(TAG, "Not writing city with empty name");
            return;
        }

        DocumentReference docRef = citiesRef.document(city.getName());

        Map<String, Object> data = new HashMap<>();
        data.put("name", city.getName());
        data.put("province", city.getProvince());

        docRef.set(data)
                .addOnSuccessListener(unused -> Log.d(TAG, "City saved to Firestore: " + city.getName()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed saving city to Firestore: " + city.getName(), e));
    }
}