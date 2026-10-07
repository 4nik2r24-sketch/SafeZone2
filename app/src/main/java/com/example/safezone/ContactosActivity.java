package com.example.safezone;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ContactosActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SafeZoneContactosPrefs";
    private static final String KEY_CONTACTOS = "lista_contactos_json";
    private static final int MAX_CONTACTOS = 3;

    private EditText etNombreContacto, etTelefonoContacto;
    private Button btnGuardarContacto, btnSeleccionarDeAgenda;
    private TextView tvLimiteContactos, tvSinContactos;
    private LinearLayout contenedorListaContactos;

    private final List<Contacto> listaContactos = new ArrayList<>();

    private final ActivityResultLauncher<Intent> selectorTelefonoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    if (contactUri != null) {
                        extraerDatosTelefono(contactUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contactos);

        TextView btnVolverContactos = findViewById(R.id.btnVolverContactos);
        etNombreContacto = findViewById(R.id.etNombreContacto);
        etTelefonoContacto = findViewById(R.id.etTelefonoContacto);
        btnGuardarContacto = findViewById(R.id.btnGuardarContacto);
        btnSeleccionarDeAgenda = findViewById(R.id.btnSeleccionarDeAgenda);
        tvLimiteContactos = findViewById(R.id.tvLimiteContactos);
        tvSinContactos = findViewById(R.id.tvSinContactos);
        contenedorListaContactos = findViewById(R.id.contenedorListaContactos);

        btnVolverContactos.setOnClickListener(v -> finish());

        cargarContactosDesdeAlmacenamiento();
        actualizarVistaLista();

        btnSeleccionarDeAgenda.setOnClickListener(v -> abrirSelectorDeTelefonos());
        btnGuardarContacto.setOnClickListener(v -> agregarNuevoContacto());
    }

    private void abrirSelectorDeTelefonos() {
        if (listaContactos.size() >= MAX_CONTACTOS) {
            Toast.makeText(this, "Límite alcanzado: Máximo 3 contactos", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            selectorTelefonoLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir contactos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void extraerDatosTelefono(Uri uri) {
        String[] proyeccion = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(uri, proyeccion, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int indiceNombre = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int indiceNumero = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                String nombre = (indiceNombre != -1) ? cursor.getString(indiceNombre) : "";
                String telefono = (indiceNumero != -1) ? cursor.getString(indiceNumero) : "";

                if (telefono != null) {
                    telefono = telefono.replaceAll("[\\s\\-\\(\\)]", "");
                }

                etNombreContacto.setText(nombre);
                etTelefonoContacto.setText(telefono);
                Toast.makeText(this, "Contacto seleccionado", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al leer contacto: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void agregarNuevoContacto() {
        if (listaContactos.size() >= MAX_CONTACTOS) {
            Toast.makeText(this, "Límite alcanzado: Máximo 3 contactos", Toast.LENGTH_SHORT).show();
            return;
        }

        String nombre = etNombreContacto.getText().toString().trim();
        String telefono = etTelefonoContacto.getText().toString().trim();

        if (nombre.isEmpty() || telefono.isEmpty()) {
            Toast.makeText(this, "Selecciona o escribe nombre y teléfono", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = String.valueOf(System.currentTimeMillis());
        listaContactos.add(new Contacto(id, nombre, telefono));

        guardarContactosEnAlmacenamiento();
        actualizarVistaLista();

        etNombreContacto.setText("");
        etTelefonoContacto.setText("");
        Toast.makeText(this, "Contacto guardado", Toast.LENGTH_SHORT).show();
    }

    private void actualizarVistaLista() {
        contenedorListaContactos.removeAllViews();
        tvLimiteContactos.setText("Máximo 3 contactos autorizados (" + listaContactos.size() + "/" + MAX_CONTACTOS + ").");

        if (listaContactos.isEmpty()) {
            contenedorListaContactos.addView(tvSinContactos);
            tvSinContactos.setVisibility(View.VISIBLE);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < listaContactos.size(); i++) {
            Contacto c = listaContactos.get(i);
            View viewItem = inflater.inflate(R.layout.item_contacto, contenedorListaContactos, false);

            TextView tvNombre = viewItem.findViewById(R.id.tvNombreContactoItem);
            TextView tvTelefono = viewItem.findViewById(R.id.tvTelefonoContactoItem);
            Button btnEliminar = viewItem.findViewById(R.id.btnEliminarContactoItem);

            String rol = (i == 0) ? " (Llamada SOS)" : (i == 1 ? " (SMS Alerta)" : " (Respaldo)");
            tvNombre.setText(c.getNombre() + rol);
            tvTelefono.setText(c.getTelefono());

            btnEliminar.setOnClickListener(v -> {
                listaContactos.remove(c);
                guardarContactosEnAlmacenamiento();
                actualizarVistaLista();
                Toast.makeText(ContactosActivity.this, "Contacto eliminado", Toast.LENGTH_SHORT).show();
            });

            contenedorListaContactos.addView(viewItem);
        }
    }

    private void guardarContactosEnAlmacenamiento() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();

        for (Contacto c : listaContactos) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", c.getId());
                obj.put("nombre", c.getNombre());
                obj.put("telefono", c.getTelefono());
                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefs.edit().putString(KEY_CONTACTOS, jsonArray.toString()).apply();
    }

    private void cargarContactosDesdeAlmacenamiento() {
        listaContactos.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String data = prefs.getString(KEY_CONTACTOS, null);

        if (data != null) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    listaContactos.add(new Contacto(
                            obj.getString("id"),
                            obj.getString("nombre"),
                            obj.getString("telefono")
                    ));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}