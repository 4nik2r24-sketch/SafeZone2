package com.example.safezone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

    private static final String TAG = "SafeZoneMain";

    private static final XYTileSource FUENTE_LIBRE = new XYTileSource(
            "OSMFrance", 0, 19, 256, ".png",
            new String[]{
                    "https://a.tile.openstreetmap.fr/osmfr/",
                    "https://b.tile.openstreetmap.fr/osmfr/",
                    "https://c.tile.openstreetmap.fr/osmfr/"
            });

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

    private AutoCompleteTextView txtBuscar;
    private ImageView btnLimpiarBuscador;
    private DireccionAdapter direccionAdapter;
    private Marker marcadorLugarBuscado;

    private final List<Incidente> listaIncidentes = new ArrayList<>();
    private final List<ZonaRiesgo> listaZonas = new ArrayList<>();

    private final androidx.activity.result.ActivityResultLauncher<Intent> reporteLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            procesarNuevoReporte(
                                    data.getDoubleExtra("LAT", -20.2155),
                                    data.getDoubleExtra("LON", -70.1513),
                                    data.getStringExtra("TIPO_CRIMEN"),
                                    data.getStringExtra("HORARIO"),
                                    data.getStringExtra("DESCRIPCION")
                            );
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue("SafeZone_Iquique_V4/1.0");
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE));
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(150L * 1024 * 1024);

        setContentView(R.layout.activity_main);
        solicitarPermisos();

        mapa = findViewById(R.id.map);
        mapa.setTileSource(FUENTE_LIBRE);
        mapa.setMultiTouchControls(true);
        mapa.getController().setZoom(15.0);
        mapa.getController().setCenter(new GeoPoint(-20.2155, -70.1513));

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

        txtBuscar = findViewById(R.id.txtBuscar);
        btnLimpiarBuscador = findViewById(R.id.btnLimpiarBuscador);
        View cardBuscador = findViewById(R.id.capaSuperior);

        txtBuscar.setThreshold(3);
        txtBuscar.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        if (cardBuscador != null) {
            txtBuscar.setDropDownAnchor(cardBuscador.getId());
        }

        // BÚSQUEDA NATIVA: El adaptador se encarga de todo el trabajo pesado en segundo plano
        direccionAdapter = new DireccionAdapter(this);
        txtBuscar.setAdapter(direccionAdapter);

        // Controlamos únicamente la visibilidad del botón "X" para limpiar
        txtBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnLimpiarBuscador != null) {
                    btnLimpiarBuscador.setVisibility((s != null && s.length() > 0) ? View.VISIBLE : View.GONE);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (btnLimpiarBuscador != null) {
            btnLimpiarBuscador.setOnClickListener(v -> {
                txtBuscar.setText("");
                txtBuscar.clearFocus();
                direccionAdapter.actualizarDatos(new ArrayList<>());
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(txtBuscar.getWindowToken(), 0);
            });
        }

        txtBuscar.setOnItemClickListener((parent, view, position, id) -> {
            ResultadoDireccion dir = direccionAdapter.getItem(position);
            if (dir != null) {
                txtBuscar.setText(dir.getNombre(), false);
                txtBuscar.clearFocus();

                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(txtBuscar.getWindowToken(), 0);

                GeoPoint destino = new GeoPoint(dir.getLatitud(), dir.getLongitud());
                mapa.getController().animateTo(destino);
                mapa.getController().setZoom(17.5);

                if (marcadorLugarBuscado != null) mapa.getOverlays().remove(marcadorLugarBuscado);

                marcadorLugarBuscado = new Marker(mapa);
                marcadorLugarBuscado.setPosition(destino);
                marcadorLugarBuscado.setTitle("Destino Consultado");
                marcadorLugarBuscado.setSnippet(dir.getNombre());
                marcadorLugarBuscado.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                // SOLUCIÓN DEFINITIVA DE DISEÑO: Asignamos el ícono vectorial celeste a la búsqueda
                marcadorLugarBuscado.setIcon(androidx.core.content.ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_pin_busqueda));

                mapa.getOverlays().add(marcadorLugarBuscado);
                mapa.invalidate();
                evaluarPanoramaSector(destino, dir.getNombre());
            }
        });

        if (btnPerfilContacto != null) btnPerfilContacto.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactosActivity.class)));
        if (btnFlotanteSOS != null) btnFlotanteSOS.setOnClickListener(v -> ejecutarProtocoloSOS());
        if (btnRutaSegura != null) btnRutaSegura.setOnClickListener(v -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));

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
    }

    private void evaluarPanoramaSector(GeoPoint puntoConsultado, String nombreLugar) {
        ZonaRiesgo zonaEncontrada = null;
        for (ZonaRiesgo z : listaZonas) {
            double radioZona = 60.0 + ((z.totalReportes - 1) * 25.0);
            if (z.centro.distanceToAsDouble(puntoConsultado) <= radioZona) {
                zonaEncontrada = z;
                break;
            }
        }

        String encabezado = nombreLugar.contains(",") ? nombreLugar.split(",")[0].trim() : nombreLugar;
        tvTituloSector.setText("Ubicación: " + (encabezado.length() > 30 ? encabezado.substring(0, 30) + "..." : encabezado));

        if (zonaEncontrada != null) {
            int total = zonaEncontrada.totalReportes;
            tvTipoCrimen.setText(total <= 4 ? "🟠 PELIGRO: " + total + " Reportes" : "🔴 ZONA CRÍTICA: " + total + " Delitos");
            tvHorario.setText("Nivel de Riesgo: Elevado");
            tvDescripcion.setText("Incidentes recientes en este cuadrante.");
        } else {
            tvTipoCrimen.setText("🛡️ SECTOR SEGURO / SIN REPORTES");
            tvHorario.setText("Estado: Despejado");
            tvDescripcion.setText("No se registran incidentes en un radio de 300 metros.");
        }

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
    private void ejecutarProtocoloSOS() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.CALL_PHONE,
                    android.Manifest.permission.SEND_SMS,
                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 200);
            return;
        }

        java.util.List<Contacto> contactos = obtenerContactosEmergencia();
        if (contactos.isEmpty()) {
            android.widget.Toast.makeText(this, "Agrega tus contactos primero.", android.widget.Toast.LENGTH_LONG).show();
            startActivity(new android.content.Intent(MainActivity.this, ContactosActivity.class));
            return;
        }

        android.widget.Toast.makeText(this, "Iniciando SOS y rastreando ubicación en vivo...", android.widget.Toast.LENGTH_SHORT).show();

        // 1. Ejecutar la llamada de inmediato al Contacto 1 para no perder tiempo
        String telefonoLlamada = contactos.get(0).getTelefono();
        android.content.Intent callIntent = new android.content.Intent(android.content.Intent.ACTION_CALL);
        callIntent.setData(android.net.Uri.parse("tel:" + telefonoLlamada));
        startActivity(callIntent);

        // 2. Obligar al GPS a dar una coordenada en tiempo real para el SMS
        if (contactos.size() >= 2) {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
            if (lm != null) {
                // Intenta primero con la máxima precisión satelital (GPS_PROVIDER)
                lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        enviarSMSEmergencia(contactos.get(1), location);
                    }
                    @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {
                        // Si falla o el usuario está en interiores, cambia a la antena de celular (NETWORK_PROVIDER)
                        lm.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, new android.location.LocationListener() {
                            @Override
                            public void onLocationChanged(android.location.Location location) {
                                enviarSMSEmergencia(contactos.get(1), location);
                            }
                            @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
                            @Override public void onProviderEnabled(String provider) {}
                            @Override public void onProviderDisabled(String provider) {
                                // Último recurso: usar la caché si ambos sensores fallan
                                enviarSMSEmergencia(contactos.get(1), lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER));
                            }
                        }, null);
                    }
                }, null);
            }
        }
    }

    private void enviarSMSEmergencia(Contacto contactoSecundario, android.location.Location ubicacionReal) {
        String mensajeSMS = "¡ALERTA SOS! Me encuentro en peligro.";

        if (ubicacionReal != null) {
            mensajeSMS += " Mi ubicación exacta EN VIVO: https://maps.google.com/?q=" + ubicacionReal.getLatitude() + "," + ubicacionReal.getLongitude();
        } else {
            mensajeSMS += " (Sin señal satelital disponible).";
        }

        try {
            android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
            // sendMultipartTextMessage asegura que el enlace largo de Google Maps no se corte
            java.util.ArrayList<String> partes = smsManager.divideMessage(mensajeSMS);
            smsManager.sendMultipartTextMessage(contactoSecundario.getTelefono(), null, partes, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private List<Contacto> obtenerContactosEmergencia() {
        List<Contacto> lista = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("SafeZoneContactosPrefs", MODE_PRIVATE);
        String data = prefs.getString("lista_contactos_json", null);
        if (data != null) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    lista.add(new Contacto(obj.getString("id"), obj.getString("nombre"), obj.getString("telefono")));
                }
            } catch (JSONException ignored) {}
        }
        return lista;
    }

    private void solicitarPermisos() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS}, 101);
    }

    private void iniciarReporteConUbicacionDispositivo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermisos();
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location mejorUbicacion = null;

        if (locationManager != null) {
            Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gpsLoc != null && netLoc != null) mejorUbicacion = (gpsLoc.getTime() > netLoc.getTime()) ? gpsLoc : netLoc;
            else if (gpsLoc != null) mejorUbicacion = gpsLoc;
            else mejorUbicacion = netLoc;
        }

        if (mejorUbicacion != null) {
            final double lat = mejorUbicacion.getLatitude();
            final double lon = mejorUbicacion.getLongitude();

            new AlertDialog.Builder(this)
                    .setTitle("Nuevo Reporte")
                    .setMessage("¿Reportar incidente en tu ubicación actual?")
                    .setPositiveButton("Reportar", (dialog, which) -> {
                        Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                        intent.putExtra("LAT_MAPA", lat);
                        intent.putExtra("LON_MAPA", lon);
                        reporteLauncher.launch(intent);
                    })
                    .setNegativeButton("Cancelar", null).show();
        } else {
            Toast.makeText(this, "Esperando señal GPS...", Toast.LENGTH_LONG).show();
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
        } else {
            Polygon perimetro = new Polygon();
            perimetro.setPoints(Polygon.pointsAsCircle(nuevoPunto, 60.0));
            perimetro.getOutlinePaint().setColor(Color.parseColor("#10B981"));
            perimetro.getOutlinePaint().setStrokeWidth(3.0f);
            perimetro.getFillPaint().setColor(Color.argb(60, 16, 185, 129));
            mapa.getOverlays().add(0, perimetro);
            listaZonas.add(new ZonaRiesgo(nuevoPunto, perimetro));
        }
        mapa.getController().animateTo(nuevoPunto);
        mapa.invalidate();
    }

    private void actualizarZonaVisual(ZonaRiesgo zona) {
        double radio = 60.0 + ((zona.totalReportes - 1) * 25.0);
        zona.perimetroVisual.setPoints(Polygon.pointsAsCircle(zona.centro, radio));
        int colorBorde = (zona.totalReportes == 2) ? Color.parseColor("#FBBF24") : (zona.totalReportes <= 4 ? Color.parseColor("#F97316") : Color.parseColor("#EF4444"));
        int colorRelleno = (zona.totalReportes == 2) ? Color.argb(70, 251, 191, 36) : (zona.totalReportes <= 4 ? Color.argb(80, 249, 115, 22) : Color.argb(95, 239, 68, 68));
        zona.perimetroVisual.getOutlinePaint().setColor(colorBorde);
        zona.perimetroVisual.getFillPaint().setColor(colorRelleno);
    }

    private void agregarMarcador(GeoPoint punto, String tipoCrimen, String horario, String descripcion) {
        Marker marcador = new Marker(mapa);
        marcador.setPosition(punto);
        marcador.setTitle(tipoCrimen);
        marcador.setSnippet(descripcion);
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // SOLUCIÓN BUG: Forzamos el color ROJO explícitamente para que no herede la forma ni tinte del buscador
        if (marcador.getIcon() != null) {
            marcador.getIcon().mutate().clearColorFilter();
            marcador.getIcon().mutate().setColorFilter(Color.parseColor("#EF4444"), PorterDuff.Mode.SRC_IN);
        }

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
    protected void onResume() { super.onResume(); if (mapa != null) mapa.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (mapa != null) mapa.onPause(); }

    private void borrarDirectorio(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] hijos = dir.list();
            if (hijos != null) for (String hijo : hijos) borrarDirectorio(new File(dir, hijo));
            dir.delete();
        }
    }
}