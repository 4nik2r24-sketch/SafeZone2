package com.example.safezone; // Usa tu paquete real

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ReportActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // Darle vida al botón de volver
        TextView btnVolverReporte = findViewById(R.id.btnVolverReporte);
        btnVolverReporte.setOnClickListener(v -> finish()); // Esto cierra la pantalla
    }
}