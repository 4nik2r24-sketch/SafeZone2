package com.example.safezone; // Asegúrate de usar tu nombre de paquete exacto

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

// Nuevas importaciones de Osmdroid
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MainActivity extends AppCompatActivity {

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvTituloSector, tvHorario, tvTipoCrimen, tvDescripcion;
    private FloatingActionButton btnFlotanteReportar, btnFlotanteSOS;
    private CardView btnPerfilContacto;
    private MapView mapa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inicializar configuración de OpenStreetMap (MUY IMPORTANTE hacerlo antes de setContentView)
        // NUEVA LÍNEA (Tu credencial única)
        Configuration.getInstance().setUserAgentValue("SafeZoneApp_Seguridad/1.0 (4nik2r24@gmail.com)");

        setContentView(R.layout.activity_main);

        // 2. Configurar el Mapa
        mapa = findViewById(R.id.map);
        mapa.setMultiTouchControls(true); // Permitir hacer zoom con los dedos
        mapa.getController().setZoom(15.0); // Nivel de zoom inicial

        // Centrar el mapa en una coordenada de prueba
        GeoPoint puntoInicio = new GeoPoint(-20.2155, -70.1513);
        mapa.getController().setCenter(puntoInicio);

        // 3. Configurar el Panel Deslizable
        View bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // 4. Conectar Interfaz
        tvTituloSector = findViewById(R.id.tvTituloSector);
        tvHorario = findViewById(R.id.tvHorario);
        tvTipoCrimen = findViewById(R.id.tvTipoCrimen);
        tvDescripcion = findViewById(R.id.tvDescripcion);
        btnFlotanteReportar = findViewById(R.id.btnFlotanteReportar);
        btnFlotanteSOS = findViewById(R.id.btnFlotanteSOS);
        btnPerfilContacto = findViewById(R.id.btnPerfilContacto);

        // 5. Navegación y Botones
        btnFlotanteReportar.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ReportActivity.class));
        });

        btnPerfilContacto.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ContactosActivity.class));
        });

        btnFlotanteSOS.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "SOS ACTIVADO", Toast.LENGTH_LONG).show();
        });
    }

    // Buenas prácticas para Osmdroid: pausar el mapa cuando la app está en segundo plano
    @Override
    protected void onResume() {
        super.onResume();
        if (mapa != null) mapa.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapa != null) mapa.onPause();
    }
}