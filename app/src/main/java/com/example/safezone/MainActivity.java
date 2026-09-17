package com.example.safezone;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static class ZonaRiesgo {
        GeoPoint centro;
        int totalReportes;
        Polygon perimetroVisual;

        ZonaRiesgo(GeoPoint centro, Polygon perimetroVisual) {
            this.centro = centro;
            this.totalReportes = 1;
            this.perimetroVisual = perimetroVisual;
        }
    }

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvTituloSector, tvHorario, tvTipoCrimen, tvDescripcion;
    private FloatingActionButton btnFlotanteSOS;
    private CardView btnPerfilContacto;
    private MapView mapa;

    private final List<Incidente> listaIncidentes = new ArrayList<>();
    private final List<ZonaRiesgo> listaZonas = new ArrayList<>();

    private final androidx.activity.result.ActivityResultLauncher<Intent> reporteLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            String tipo = data.getStringExtra("TIPO_CRIMEN");
                            String horario = data.getStringExtra("HORARIO");
                            String desc = data.getStringExtra("DESCRIPCION");
                            double lat = data.getDoubleExtra("LAT", -20.2155);
                            double lon = data.getDoubleExtra("LON", -70.1513);

                            procesarNuevoReporte(lat, lon, tipo, horario, desc);
                        }
                    });

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

        btnFlotanteSOS = findViewById(R.id.btnFlotanteSOS);
        btnPerfilContacto = findViewById(R.id.btnPerfilContacto);

        btnPerfilContacto.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactosActivity.class)));
        btnFlotanteSOS.setOnClickListener(v -> Toast.makeText(MainActivity.this, "SOS ACTIVADO", Toast.LENGTH_LONG).show());

        MapEventsReceiver receptorEventos = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                mostrarDialogoConfirmacion(p);
                return true;
            }
        };

        mapa.getOverlays().add(new MapEventsOverlay(receptorEventos));
        mapa.invalidate();
    }

    private void mostrarDialogoConfirmacion(GeoPoint p) {
        new AlertDialog.Builder(this)
                .setTitle("Nuevo Reporte")
                .setMessage("¿Deseas reportar un incidente en esta ubicación?")
                .setPositiveButton("Reportar", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                    intent.putExtra("LAT_MAPA", p.getLatitude());
                    intent.putExtra("LON_MAPA", p.getLongitude());
                    reporteLauncher.launch(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void procesarNuevoReporte(double lat, double lon, String tipo, String horario, String desc) {
        GeoPoint nuevoPunto = new GeoPoint(lat, lon);

        Incidente nuevoIncidente = new Incidente(String.valueOf(System.currentTimeMillis()), lat, lon, tipo, desc, horario);
        listaIncidentes.add(nuevoIncidente);
        agregarMarcador(nuevoPunto, tipo, horario, desc);

        ZonaRiesgo zonaCercana = null;
        for (ZonaRiesgo z : listaZonas) {
            if (z.centro.distanceToAsDouble(nuevoPunto) <= 150.0) {
                zonaCercana = z;
                break;
            }
        }

        if (zonaCercana != null) {
            zonaCercana.totalReportes++;
            actualizarZonaVisual(zonaCercana);
            Toast.makeText(this, "Zona de riesgo agravada (" + zonaCercana.totalReportes + " reportes)", Toast.LENGTH_SHORT).show();
        } else {
            crearNuevaZonaVisual(nuevoPunto);
            Toast.makeText(this, "Incidente registrado", Toast.LENGTH_SHORT).show();
        }

        mapa.invalidate();
    }

    private void crearNuevaZonaVisual(GeoPoint punto) {
        Polygon perimetro = new Polygon();
        perimetro.setPoints(Polygon.pointsAsCircle(punto, 60.0));

        perimetro.getOutlinePaint().setColor(Color.parseColor("#10B981"));
        perimetro.getOutlinePaint().setStrokeWidth(3.0f);
        perimetro.getFillPaint().setColor(Color.argb(60, 16, 185, 129));

        mapa.getOverlays().add(0, perimetro);
        listaZonas.add(new ZonaRiesgo(punto, perimetro));
    }

    private void actualizarZonaVisual(ZonaRiesgo zona) {
        double radio = 60.0 + ((zona.totalReportes - 1) * 25.0);
        zona.perimetroVisual.setPoints(Polygon.pointsAsCircle(zona.centro, radio));

        int colorBorde;
        int colorRelleno;

        if (zona.totalReportes == 2) {
            colorBorde = Color.parseColor("#FBBF24");
            colorRelleno = Color.argb(70, 251, 191, 36);
        } else if (zona.totalReportes <= 4) {
            colorBorde = Color.parseColor("#F97316");
            colorRelleno = Color.argb(80, 249, 115, 22);
        } else {
            colorBorde = Color.parseColor("#EF4444");
            colorRelleno = Color.argb(95, 239, 68, 68);
        }

        zona.perimetroVisual.getOutlinePaint().setColor(colorBorde);
        zona.perimetroVisual.getFillPaint().setColor(colorRelleno);
    }

    private void agregarMarcador(GeoPoint punto, String tipoCrimen, String horario, String descripcion) {
        Marker marcador = new Marker(mapa);
        marcador.setPosition(punto);
        marcador.setTitle(tipoCrimen);
        marcador.setSnippet(descripcion);
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        marcador.setOnMarkerClickListener((marker, mapView) -> {
            tvTituloSector.setText("Incidente Reportado");
            tvTipoCrimen.setText(marker.getTitle());
            tvDescripcion.setText(marker.getSnippet());
            tvHorario.setText("Horario crítico: " + horario);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            return true;
        });

        mapa.getOverlays().add(marcador);
    }

    @Override
    protected void onResume() { super.onResume(); if (mapa != null) mapa.onResume(); }

    @Override
    protected void onPause() { super.onPause(); if (mapa != null) mapa.onPause(); }
}