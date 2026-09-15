package com.example.safezone;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter; // <-- Importante para las listas
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class ReportActivity extends AppCompatActivity {

    private double latSeleccionada = -20.2155;
    private double lonSeleccionada = -70.1513;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        TextView btnVolverReporte = findViewById(R.id.btnVolverReporte);
        btnVolverReporte.setOnClickListener(v -> finish());

        // 1. Conectar los campos de texto de la interfaz
        AutoCompleteTextView dropTipoCrimen = findViewById(R.id.dropTipoCrimen);
        AutoCompleteTextView dropHorario = findViewById(R.id.dropHorario); // Agregamos el horario
        TextInputEditText etDescripcion = findViewById(R.id.etDescripcion);
        Button btnEnviarReporte = findViewById(R.id.btnEnviarReporte);

        // 2. CREAR LAS OPCIONES PARA LOS MENÚS
        // Opciones para "Tipo de Crimen"
        String[] opcionesCrimen = {"Robo", "Hurto", "Actividad Sospechosa", "Vandalismo", "Acoso"};
        ArrayAdapter<String> adapterCrimen = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, opcionesCrimen);
        dropTipoCrimen.setAdapter(adapterCrimen);

        // Opciones para "Horario"
        String[] opcionesHorario = {"Reciente (última hora)", "Hoy temprano", "Ayer", "Otro día"};
        ArrayAdapter<String> adapterHorario = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, opcionesHorario);
        dropHorario.setAdapter(adapterHorario);

        // 3. Lógica del botón enviar
        btnEnviarReporte.setOnClickListener(v -> {
            String tipoCrimen = dropTipoCrimen.getText().toString();
            String descripcion = etDescripcion.getText() != null ? etDescripcion.getText().toString() : "";

            // Validación básica
            if (tipoCrimen.isEmpty()) {
                tipoCrimen = "Alerta General";
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra("TIPO_CRIMEN", tipoCrimen);
            resultIntent.putExtra("DESCRIPCION", descripcion);
            resultIntent.putExtra("LAT", latSeleccionada);
            resultIntent.putExtra("LON", lonSeleccionada);

            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}