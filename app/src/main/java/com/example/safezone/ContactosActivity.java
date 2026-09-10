package com.example.safezone; // Usa tu paquete real

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ContactosActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contactos);

        // Darle vida al botón de volver
        TextView btnVolverContactos = findViewById(R.id.btnVolverContactos);
        btnVolverContactos.setOnClickListener(v -> finish()); // Esto cierra la pantalla
    }
}