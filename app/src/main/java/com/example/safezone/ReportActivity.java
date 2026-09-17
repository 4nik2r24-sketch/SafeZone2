package com.example.safezone;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private double latSeleccionada = -20.2155;
    private double lonSeleccionada = -70.1513;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        latSeleccionada = getIntent().getDoubleExtra("LAT_MAPA", -20.2155);
        lonSeleccionada = getIntent().getDoubleExtra("LON_MAPA", -70.1513);

        TextView tvUbicacionMarcada = findViewById(R.id.tvUbicacionMarcada);
        tvUbicacionMarcada.setText(String.format(Locale.US, "📍 Ubicación: %.4f, %.4f", latSeleccionada, lonSeleccionada));

        TextView btnVolverReporte = findViewById(R.id.btnVolverReporte);
        btnVolverReporte.setOnClickListener(v -> finish());

        AutoCompleteTextView dropTipoCrimen = findViewById(R.id.dropTipoCrimen);
        AutoCompleteTextView dropHorario = findViewById(R.id.dropHorario);
        TextInputEditText etDescripcion = findViewById(R.id.etDescripcion);
        Button btnEnviarReporte = findViewById(R.id.btnEnviarReporte);

        // Tipos de delito
        String[] opcionesCrimen = {
                "Robo con violencia",
                "Robo por sorpresa (Lanzazo)",
                "Hurto",
                "Actividad sospechosa",
                "Acoso callejero",
                "Vandalismo"
        };
        dropTipoCrimen.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, opcionesCrimen));

        // Rangos estructurados en formato 24 horas
        String[] opcionesHorario24h = {
                "00:00 - 04:00 (Madrugada)",
                "04:00 - 08:00 (Mañana temprano)",
                "08:00 - 12:00 (Mañana)",
                "12:00 - 16:00 (Tarde)",
                "16:00 - 20:00 (Atardecer)",
                "20:00 - 00:00 (Noche)"
        };
        dropHorario.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, opcionesHorario24h));

        btnEnviarReporte.setOnClickListener(v -> {
            String tipoCrimen = dropTipoCrimen.getText().toString().trim();
            String horario = dropHorario.getText().toString().trim();
            String descripcion = etDescripcion.getText() != null ? etDescripcion.getText().toString().trim() : "";

            if (tipoCrimen.isEmpty()) tipoCrimen = "Alerta General";
            if (horario.isEmpty()) horario = "Sin horario especificado";

            Intent resultIntent = new Intent();
            resultIntent.putExtra("TIPO_CRIMEN", tipoCrimen);
            resultIntent.putExtra("HORARIO", horario);
            resultIntent.putExtra("DESCRIPCION", descripcion);
            resultIntent.putExtra("LAT", latSeleccionada);
            resultIntent.putExtra("LON", lonSeleccionada);

            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}