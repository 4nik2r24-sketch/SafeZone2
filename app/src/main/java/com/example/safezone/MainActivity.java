package com.example.safezone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Servidor público abierto internacional (NO requiere API Key y NO tiene el bloqueo estricto 403)
    private static final XYTileSource FUENTE_LIBRE = new XYTileSource(
            "OSMFrance",
            0, 19, 256, ".png",
            new String[]{
                    "https://a.tile.openstreetmap.fr/osmfr/",
                    "https://b.tile.openstreetmap.fr/osmfr/",
                    "https://c.tile.openstreetmap.fr/osmfr/"
            }
    );

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
    private Button btnRutaSegura;
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

        // 1. Limpieza de imágenes corruptas previas (403 o "API key required") guardadas en caché
        File cacheOsm = new File(getCacheDir(), "osmdroid");
        borrarDirectorio(cacheOsm);

        // 2. Configuración obligatoria de Osmdroid
        Configuration.getInstance().setUserAgentValue("SafeZone_Iquique_V4/1.0 (contacto@safezone.cl)");
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE));
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(150L * 1024 * 1024);

        setContentView(R.layout.activity_main);

        solicitarPermisosUbicacion();

        // 3. Inicialización del mapa con el servidor libre
        mapa = findViewById(R.id.map);
        mapa.setTileSource(FUENTE_LIBRE);
        mapa.setMultiTouchControls(true);
        mapa.getController().setZoom(15.0);
        mapa.getController().setCenter(new GeoPoint(-20.2155, -70.1513));

        // 4. Panel deslizable y vistas
        View bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        tvTituloSector = findViewById(R.id.tvTituloSector);
        tvHorario = findViewById(R.id.tvHorario);
        tvTipoCrimen = findViewById(R.id.tvTipoCrimen);
        tvDescripcion = findViewById(R.id.tvDescripcion);

        btnFlotanteSOS = findViewById(R.id.btnFlotanteSOS);
        btnPerfilContacto = findViewById(R.id.btnPerfilContacto);
        btnRutaSegura = findViewById(R.id.btnRutaSegura);

        if (btnPerfilContacto != null) {
            btnPerfilContacto.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactosActivity.class)));
        }

        if (btnFlotanteSOS != null) {
            btnFlotanteSOS.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "SOS ACTIVADO: Alerta transmitida", Toast.LENGTH_LONG).show());
        }

        if (btnRutaSegura != null) {
            btnRutaSegura.setOnClickListener(v -> {
                Toast.makeText(MainActivity.this, "Calculando ruta segura alternativa...", Toast.LENGTH_SHORT).show();
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            });
        }

        // 5. Toque sostenido (Long Press): Consulta la posición física del GPS del celular
        MapEventsReceiver receptorEventos = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                iniciarReporteConUbicacionDispositivo();
                return true;
            }
        };

        mapa.getOverlays().add(new MapEventsOverlay(receptorEventos));
        mapa.invalidate();
    }

    private void borrarDirectorio(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] hijos = dir.list();
            if (hijos != null) {
                for (String hijo : hijos) {
                    borrarDirectorio(new File(dir, hijo));
                }
            }
            dir.delete();
        }
    }

    private void solicitarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
    }

    private void iniciarReporteConUbicacionDispositivo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Se requiere permiso de ubicación para reportar", Toast.LENGTH_SHORT).show();
            solicitarPermisosUbicacion();
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location mejorUbicacion = null;

        if (locationManager != null) {
            Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (gpsLoc != null && netLoc != null) {
                mejorUbicacion = (gpsLoc.getTime() > netLoc.getTime()) ? gpsLoc : netLoc;
            } else if (gpsLoc != null) {
                mejorUbicacion = gpsLoc;
            } else {
                mejorUbicacion = netLoc;
            }
        }

        if (mejorUbicacion != null) {
            final double lat = mejorUbicacion.getLatitude();
            final double lon = mejorUbicacion.getLongitude();

            new AlertDialog.Builder(this)
                    .setTitle("Nuevo Reporte")
                    .setMessage("¿Deseas reportar un incidente en la ubicación actual detectada por el celular?")
                    .setPositiveButton("Reportar", (dialog, which) -> {
                        Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                        intent.putExtra("LAT_MAPA", lat);
                        intent.putExtra("LON_MAPA", lon);
                        reporteLauncher.launch(intent);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            Toast.makeText(this, "Esperando señal GPS... Verifica que la ubicación esté encendida", Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(this, "Incidente registrado en tu posición", Toast.LENGTH_SHORT).show();
        }

        mapa.getController().animateTo(nuevoPunto);
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
            tvHorario.setText("Horario: " + horario);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            return true;
        });

        mapa.getOverlays().add(marcador);
        mapa.invalidate();
    }

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