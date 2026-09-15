package com.example.safezone;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MainActivity extends AppCompatActivity {

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvTituloSector, tvHorario, tvTipoCrimen, tvDescripcion;
    private FloatingActionButton btnFlotanteReportar, btnFlotanteSOS;
    private CardView btnPerfilContacto;
    private MapView mapa;

    // Botones de Modo
    private Button btnModoNormal, btnModoDibujo, btnModoBorrar;

    // Variables para el modo interactivo
    private java.util.List<GeoPoint> puntosTemporales = new java.util.ArrayList<>();
    private java.util.List<org.osmdroid.views.overlay.Polygon> zonasDibujadas = new java.util.ArrayList<>();

    // 0 = Mover, 1 = Dibujar, 2 = Borrar
    private int modoActual = 1;

    private final androidx.activity.result.ActivityResultLauncher<Intent> reporteLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            String tipo = data.getStringExtra("TIPO_CRIMEN");
                            String desc = data.getStringExtra("DESCRIPCION");
                            double lat = data.getDoubleExtra("LAT", -20.2155);
                            double lon = data.getDoubleExtra("LON", -70.1513);

                            agregarMarcador(lat, lon, tipo, desc);
                        }
                    });

    private void dibujarZonaPoligonal(java.util.List<GeoPoint> puntos, int colorBorde, int colorRelleno) {
        org.osmdroid.views.overlay.Polygon zona = new org.osmdroid.views.overlay.Polygon();
        zona.getFillPaint().setColor(colorRelleno);
        zona.getOutlinePaint().setColor(colorBorde);
        zona.getOutlinePaint().setStrokeWidth(3.0f);

        zona.setPoints(puntos);
        mapa.getOverlays().add(zona);
        zonasDibujadas.add(zona);
        mapa.invalidate();
    }

    // LÓGICA DE BORRADO: Elimina zonas que toquen el recuadro que acabas de hacer
    private void borrarZonasEnArea(java.util.List<GeoPoint> puntosArea) {
        // 1. Calcular los límites geográficos de tus 4 puntos
        double minLat = puntosArea.get(0).getLatitude();
        double maxLat = puntosArea.get(0).getLatitude();
        double minLon = puntosArea.get(0).getLongitude();
        double maxLon = puntosArea.get(0).getLongitude();

        for (GeoPoint p : puntosArea) {
            if (p.getLatitude() < minLat) minLat = p.getLatitude();
            if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
            if (p.getLongitude() < minLon) minLon = p.getLongitude();
            if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
        }

        // 2. Buscar qué polígonos están dentro de ese recuadro
        java.util.List<org.osmdroid.views.overlay.Polygon> zonasAEliminar = new java.util.ArrayList<>();
        for (org.osmdroid.views.overlay.Polygon zona : zonasDibujadas) {
            boolean tocaArea = false;
            for (GeoPoint pZona : zona.getPoints()) {
                if (pZona.getLatitude() >= minLat && pZona.getLatitude() <= maxLat &&
                        pZona.getLongitude() >= minLon && pZona.getLongitude() <= maxLon) {
                    tocaArea = true;
                    break;
                }
            }
            if (tocaArea) zonasAEliminar.add(zona);
        }

        // 3. Eliminarlos del mapa
        for (org.osmdroid.views.overlay.Polygon zonaMala : zonasAEliminar) {
            mapa.getOverlays().remove(zonaMala);
            zonasDibujadas.remove(zonaMala);
        }

        mapa.invalidate();
        Toast.makeText(this, "Se borraron " + zonasAEliminar.size() + " zonas", Toast.LENGTH_SHORT).show();
    }

    private void agregarMarcador(double lat, double lon, String tipoCrimen, String descripcion) {
        org.osmdroid.views.overlay.Marker marcador = new org.osmdroid.views.overlay.Marker(mapa);
        marcador.setPosition(new GeoPoint(lat, lon));
        marcador.setTitle(tipoCrimen);
        marcador.setSnippet(descripcion);
        marcador.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM);

        mapa.getOverlays().add(marcador);
        mapa.invalidate();
    }

    // Actualiza los colores de los botones para saber cuál está seleccionado
    private void actualizarBotonesMenu() {
        btnModoNormal.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(modoActual == 0 ? "#38BDF8" : "#2D3748")));
        btnModoNormal.setTextColor(android.graphics.Color.parseColor(modoActual == 0 ? "#000000" : "#FFFFFF"));

        btnModoDibujo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(modoActual == 1 ? "#38BDF8" : "#2D3748")));
        btnModoDibujo.setTextColor(android.graphics.Color.parseColor(modoActual == 1 ? "#000000" : "#FFFFFF"));

        btnModoBorrar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(modoActual == 2 ? "#38BDF8" : "#2D3748")));
        btnModoBorrar.setTextColor(android.graphics.Color.parseColor(modoActual == 2 ? "#000000" : "#FFFFFF"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue("SafeZoneApp_Seguridad/1.0 (4nik2r24@gmail.com)");
        setContentView(R.layout.activity_main);

        requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        mapa = findViewById(R.id.map);
        mapa.setMultiTouchControls(true);
        mapa.getController().setZoom(15.0);
        mapa.getController().setCenter(new GeoPoint(-20.2155, -70.1513));

        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottomSheet));
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        tvTituloSector = findViewById(R.id.tvTituloSector);
        tvHorario = findViewById(R.id.tvHorario);
        tvTipoCrimen = findViewById(R.id.tvTipoCrimen);
        tvDescripcion = findViewById(R.id.tvDescripcion);
        btnFlotanteReportar = findViewById(R.id.btnFlotanteReportar);
        btnFlotanteSOS = findViewById(R.id.btnFlotanteSOS);
        btnPerfilContacto = findViewById(R.id.btnPerfilContacto);

        btnModoNormal = findViewById(R.id.btnModoNormal);
        btnModoDibujo = findViewById(R.id.btnModoDibujo);
        btnModoBorrar = findViewById(R.id.btnModoBorrar);

        // Eventos de los botones de modo
        btnModoNormal.setOnClickListener(v -> { modoActual = 0; puntosTemporales.clear(); actualizarBotonesMenu(); });
        btnModoDibujo.setOnClickListener(v -> { modoActual = 1; puntosTemporales.clear(); actualizarBotonesMenu(); });
        btnModoBorrar.setOnClickListener(v -> { modoActual = 2; puntosTemporales.clear(); actualizarBotonesMenu(); });

        btnFlotanteReportar.setOnClickListener(v -> reporteLauncher.launch(new Intent(MainActivity.this, ReportActivity.class)));
        btnPerfilContacto.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactosActivity.class)));
        btnFlotanteSOS.setOnClickListener(v -> Toast.makeText(MainActivity.this, "SOS ACTIVADO", Toast.LENGTH_LONG).show());

        org.osmdroid.events.MapEventsReceiver receptorToques = new org.osmdroid.events.MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (modoActual == 1 || modoActual == 2) {
                    puntosTemporales.add(p);
                    Toast.makeText(MainActivity.this, "Punto " + puntosTemporales.size() + " (Modo " + (modoActual==1?"Dibujo":"Borrar") + ")", Toast.LENGTH_SHORT).show();

                    if (puntosTemporales.size() == 4) {
                        if (modoActual == 1) {
                            // DIBUJAR
                            dibujarZonaPoligonal(new java.util.ArrayList<>(puntosTemporales),
                                    android.graphics.Color.RED, android.graphics.Color.argb(75, 255, 0, 0));
                        } else if (modoActual == 2) {
                            // BORRAR
                            borrarZonasEnArea(new java.util.ArrayList<>(puntosTemporales));
                        }
                        puntosTemporales.clear();
                    }
                    return true;
                }
                return false; // Si está en modoNormal (0), te deja mover el mapa tranquilo
            }

            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        };

        mapa.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(receptorToques));
        mapa.invalidate();
    }

    @Override
    protected void onResume() { super.onResume(); if (mapa != null) mapa.onResume(); }

    @Override
    protected void onPause() { super.onPause(); if (mapa != null) mapa.onPause(); }
}