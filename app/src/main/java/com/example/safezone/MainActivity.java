package com.example.safezone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        List<Marker> marcadores = new ArrayList<>();

        ZonaRiesgo(GeoPoint centro, Polygon perimetroVisual) {
            this.centro = centro;
            this.totalReportes = 1;
            this.perimetroVisual = perimetroVisual;
        }
    }

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvTituloSector, tvHorario, tvTipoCrimen, tvDescripcion;
    private FloatingActionButton btnFlotanteSOS, btnFlotanteReportar;
    private CardView btnPerfilContacto;
    private Button btnRutaSegura;
    private MapView mapa;

    private TextView btnFiltroVivo, btnFiltro24h;
    private AutoCompleteTextView txtBuscar;
    private ImageView btnLimpiarBuscador;
    private DireccionAdapter direccionAdapter;
    private Marker marcadorLugarBuscado;

    private final ExecutorService ejecutorRed = Executors.newSingleThreadExecutor();
    private final Handler handlerDebounce = new Handler(Looper.getMainLooper());
    private Runnable tareaBusqueda;

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

        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception ignored) {}

        File cacheOsm = new File(getCacheDir(), "osmdroid");
        borrarDirectorio(cacheOsm);

        Configuration.getInstance().setUserAgentValue("SafeZone_Iquique_V5.0");
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
        btnFlotanteReportar = findViewById(R.id.btnFlotanteReportar);
        btnPerfilContacto = findViewById(R.id.btnPerfilContacto);
        btnRutaSegura = findViewById(R.id.btnRutaSegura);

        txtBuscar = findViewById(R.id.txtBuscar);
        btnLimpiarBuscador = findViewById(R.id.btnLimpiarBuscador);

        btnFiltroVivo = findViewById(R.id.btnFiltroVivo);
        btnFiltro24h = findViewById(R.id.btnFiltro24h);

        configurarFiltros();

        txtBuscar.setThreshold(2);
        txtBuscar.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);

        direccionAdapter = new DireccionAdapter(this);
        txtBuscar.setAdapter(direccionAdapter);

        txtBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnLimpiarBuscador != null) {
                    btnLimpiarBuscador.setVisibility((s != null && s.length() > 0) ? View.VISIBLE : View.GONE);
                }
                if (tareaBusqueda != null) handlerDebounce.removeCallbacks(tareaBusqueda);
                if (s != null && s.toString().trim().length() >= 2) {
                    tareaBusqueda = () -> ejecutarBusquedaEnSegundoPlano(s.toString().trim());
                    handlerDebounce.postDelayed(tareaBusqueda, 350);
                } else {
                    direccionAdapter.actualizarDatos(new ArrayList<>());
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
                marcadorLugarBuscado.setTitle("Destino");
                marcadorLugarBuscado.setSnippet(dir.getNombre());
                marcadorLugarBuscado.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapa.getOverlays().add(marcadorLugarBuscado);
                mapa.invalidate();
                evaluarPanoramaSector(destino, dir.getNombre());
            }
        });

        if (btnPerfilContacto != null) btnPerfilContacto.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactosActivity.class)));
        if (btnFlotanteSOS != null) btnFlotanteSOS.setOnClickListener(v -> ejecutarProtocoloSOS());
        if (btnFlotanteReportar != null) btnFlotanteReportar.setOnClickListener(v -> iniciarReporteConUbicacionDispositivo());
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

        cargarIncidentesDesdeFirebase(24);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long tiempoHace24Horas = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
        java.util.Date fechaFiltro = new java.util.Date(tiempoHace24Horas);

        db.collection("Incidentes")
                .whereGreaterThan("timestamp", fechaFiltro)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) return;
                    if (snapshots != null) {
                        for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                double lat = dc.getDocument().getDouble("latitud") != null ? dc.getDocument().getDouble("latitud") : 0.0;
                                double lon = dc.getDocument().getDouble("longitud") != null ? dc.getDocument().getDouble("longitud") : 0.0;
                                String tipo = dc.getDocument().getString("tipoCrimen");
                                String desc = dc.getDocument().getString("descripcionDelincuente");
                                String id = dc.getDocument().getString("idDelito");
                                String horario = dc.getDocument().getString("horario");

                                boolean existe = false;
                                for (Incidente i : listaIncidentes) {
                                    if (i.getIdDelito() != null && i.getIdDelito().equals(id)) {
                                        existe = true;
                                        break;
                                    }
                                }

                                if (!existe) {
                                    GeoPoint nuevoPunto = new GeoPoint(lat, lon);
                                    Incidente nuevoIncidente = new Incidente(id, lat, lon, tipo, desc, horario != null ? horario : "N/A");
                                    listaIncidentes.add(nuevoIncidente);

                                    Marker nuevoPin = agregarMarcador(nuevoPunto, tipo, horario != null ? horario : "N/A", desc);

                                    ZonaRiesgo zonaCercana = null;
                                    for (ZonaRiesgo z : listaZonas) {
                                        if (z.centro.distanceToAsDouble(nuevoPunto) <= 150.0) {
                                            zonaCercana = z;
                                            break;
                                        }
                                    }

                                    if (zonaCercana != null) {
                                        zonaCercana.totalReportes++;
                                        zonaCercana.marcadores.add(nuevoPin);
                                        actualizarZonaVisual(zonaCercana);
                                    } else {
                                        crearNuevaZonaVisual(nuevoPunto, nuevoPin);
                                    }
                                    mapa.invalidate();
                                }
                            }
                        }
                    }
                });
    }

    private void configurarFiltros() {
        if(btnFiltroVivo != null && btnFiltro24h != null) {
            btnFiltroVivo.setOnClickListener(v -> {
                btnFiltroVivo.setBackgroundResource(R.drawable.borde_rojo);
                btnFiltroVivo.setTextColor(Color.BLACK);
                btnFiltro24h.setBackgroundColor(Color.parseColor("#171B26"));
                btnFiltro24h.setTextColor(Color.parseColor("#94A3B8"));
                cargarIncidentesDesdeFirebase(2);
                Toast.makeText(this, "Filtro En Vivo activado", Toast.LENGTH_SHORT).show();
            });

            btnFiltro24h.setOnClickListener(v -> {
                btnFiltro24h.setBackgroundResource(R.drawable.borde_rojo);
                btnFiltro24h.setTextColor(Color.BLACK);
                btnFiltroVivo.setBackgroundColor(Color.parseColor("#171B26"));
                btnFiltroVivo.setTextColor(Color.parseColor("#94A3B8"));
                cargarIncidentesDesdeFirebase(24);
                Toast.makeText(this, "Mostrando historial", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void cargarIncidentesDesdeFirebase(int horasAtras) {
        long tiempoLimite = System.currentTimeMillis() - (horasAtras * 60 * 60 * 1000L);

        FirebaseFirestore.getInstance().collection("Incidentes")
                .whereGreaterThan("timestamp", tiempoLimite)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            double lat = dc.getDocument().getDouble("latitud") != null ? dc.getDocument().getDouble("latitud") : 0.0;
                            double lon = dc.getDocument().getDouble("longitud") != null ? dc.getDocument().getDouble("longitud") : 0.0;
                            String tipo = dc.getDocument().getString("tipoCrimen");
                            String desc = dc.getDocument().getString("descripcionDelincuente");
                            String id = dc.getDocument().getString("idDelito");
                            String horario = dc.getDocument().getString("horario");

                            boolean existe = false;
                            for (Incidente i : listaIncidentes) {
                                if (i.getIdDelito() != null && i.getIdDelito().equals(id)) {
                                    existe = true; break;
                                }
                            }

                            if (!existe) {
                                GeoPoint p = new GeoPoint(lat, lon);
                                listaIncidentes.add(new Incidente(id, lat, lon, tipo, desc, horario != null ? horario : "Reporte Nube"));
                                Marker nuevoPin = agregarMarcador(p, tipo, horario != null ? horario : "N/A", desc);

                                ZonaRiesgo zonaCercana = null;
                                for (ZonaRiesgo z : listaZonas) {
                                    if (z.centro.distanceToAsDouble(p) <= 150.0) { zonaCercana = z; break; }
                                }
                                if (zonaCercana != null) {
                                    zonaCercana.totalReportes++;
                                    zonaCercana.marcadores.add(nuevoPin);
                                    actualizarZonaVisual(zonaCercana);
                                } else {
                                    crearNuevaZonaVisual(p, nuevoPin);
                                }
                                mapa.invalidate();
                            }
                        }
                    }
                });
    }

    private void ejecutarBusquedaEnSegundoPlano(String consulta) {
        ejecutorRed.execute(() -> {
            List<ResultadoDireccion> listaSugerencias = new ArrayList<>();
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                String queryCod = URLEncoder.encode(consulta, "UTF-8");
                String urlStr = "https://photon.komoot.io/api/?q=" + queryCod + "&lat=-20.2155&lon=-70.1513&limit=7&lang=es";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = reader.readLine()) != null) sb.append(linea);

                    JSONArray features = new JSONObject(sb.toString()).optJSONArray("features");
                    if (features != null) {
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject feat = features.getJSONObject(i);
                            JSONObject prop = feat.getJSONObject("properties");
                            JSONArray coord = feat.getJSONObject("geometry").getJSONArray("coordinates");

                            String nombre = prop.optString("name", "").trim();
                            String calle = prop.optString("street", "").trim();
                            String ciudad = prop.optString("city", prop.optString("locality", "")).trim();
                            String titulo = !calle.isEmpty() ? calle : (!nombre.isEmpty() ? nombre : consulta);
                            String finalLabel = !ciudad.isEmpty() ? titulo + ", " + ciudad : titulo;

                            listaSugerencias.add(new ResultadoDireccion(finalLabel, coord.getDouble(1), coord.getDouble(0)));
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { if (reader != null) reader.close(); if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
            }
            runOnUiThread(() -> {
                direccionAdapter.actualizarDatos(listaSugerencias);
                if (!listaSugerencias.isEmpty() && txtBuscar.hasFocus()) txtBuscar.showDropDown();
            });
        });
    }

    private void evaluarPanoramaSector(GeoPoint puntoConsultado, String nombreLugar) {
        ZonaRiesgo zonaEncontrada = null;
        for (ZonaRiesgo z : listaZonas) {
            double radioZona = 60.0 + ((z.totalReportes - 1) * 25.0);
            if (z.centro.distanceToAsDouble(puntoConsultado) <= radioZona) {
                zonaEncontrada = z; break;
            }
        }

        tvTituloSector.setText("Ubicación Consultada");
        if (zonaEncontrada != null) {
            int total = zonaEncontrada.totalReportes;
            tvTipoCrimen.setText(total <= 4 ? "🟠 PELIGRO: " + total + " Reportes" : "🔴 ZONA CRÍTICA: " + total + " Delitos");
            tvHorario.setText("Nivel de Riesgo: Elevado");
            tvDescripcion.setText("Incidentes ocurridos recientemente en este cuadrante.");
        } else {
            tvTipoCrimen.setText("🛡️ SECTOR SEGURO / SIN REPORTES");
            tvHorario.setText("Estado: Despejado");
            tvDescripcion.setText("No se registran incidentes recientes a la redonda.");
        }
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void ejecutarProtocoloSOS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermisos(); return;
        }

        List<Contacto> contactos = obtenerContactosEmergencia();
        if (contactos.isEmpty()) {
            Toast.makeText(this, "Agrega contactos de emergencia.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(MainActivity.this, ContactosActivity.class));
            return;
        }

        String mensajeSMS = "¡ALERTA SOS! Me encuentro en peligro.";
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) mensajeSMS += " Mi ubicación exacta: https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        }

        try {
            if (contactos.size() >= 2) SmsManager.getDefault().sendTextMessage(contactos.get(1).getTelefono(), null, mensajeSMS, null, null);
        } catch (Exception ignored) {}

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + contactos.get(0).getTelefono()));
        startActivity(callIntent);
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
        String idGenerado = String.valueOf(System.currentTimeMillis());

        Map<String, Object> reporteNube = new HashMap<>();
        reporteNube.put("idDelito", idGenerado);
        reporteNube.put("latitud", lat);
        reporteNube.put("longitud", lon);
        reporteNube.put("tipoCrimen", tipo);
        reporteNube.put("descripcionDelincuente", desc);
        reporteNube.put("horario", horario);
        reporteNube.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("Incidentes").document(idGenerado).set(reporteNube)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Alerta enviada a la red", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show());

        GeoPoint nuevoPunto = new GeoPoint(lat, lon);
        Incidente nuevoIncidente = new Incidente(idGenerado, lat, lon, tipo, desc, horario);
        listaIncidentes.add(nuevoIncidente);

        Marker nuevoPin = agregarMarcador(nuevoPunto, tipo, horario, desc);

        ZonaRiesgo zonaCercana = null;
        for (ZonaRiesgo z : listaZonas) {
            if (z.centro.distanceToAsDouble(nuevoPunto) <= 150.0) {
                zonaCercana = z;
                break;
            }
        }

        if (zonaCercana != null) {
            zonaCercana.totalReportes++;
            zonaCercana.marcadores.add(nuevoPin);
            actualizarZonaVisual(zonaCercana);
        } else {
            crearNuevaZonaVisual(nuevoPunto, nuevoPin);
        }
        mapa.getController().animateTo(nuevoPunto);
        mapa.invalidate();
    }

    // MÉTODO NUEVO: Fuerza la asignación del color al pin desde cero
    private void cambiarColorPin(Marker pin, int colorHex) {
        Drawable icono = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default);
        if (icono != null) {
            icono = icono.mutate();
            icono.setColorFilter(colorHex, PorterDuff.Mode.SRC_IN);
            pin.setIcon(icono);
        }
    }

    private void crearNuevaZonaVisual(GeoPoint punto, Marker pinInicial) {
        Polygon perimetro = new Polygon();
        perimetro.setPoints(Polygon.pointsAsCircle(punto, 60.0));

        int colorHexVerde = Color.parseColor("#10B981");
        perimetro.getOutlinePaint().setColor(colorHexVerde);
        perimetro.getOutlinePaint().setStrokeWidth(3.0f);
        perimetro.getFillPaint().setColor(Color.argb(60, 16, 185, 129));

        // Pinta el primer pin de verde
        cambiarColorPin(pinInicial, colorHexVerde);

        mapa.getOverlays().add(0, perimetro);

        ZonaRiesgo nuevaZona = new ZonaRiesgo(punto, perimetro);
        nuevaZona.marcadores.add(pinInicial);
        listaZonas.add(nuevaZona);
    }

    private void actualizarZonaVisual(ZonaRiesgo zona) {
        double radio = 60.0 + ((zona.totalReportes - 1) * 25.0);
        zona.perimetroVisual.setPoints(Polygon.pointsAsCircle(zona.centro, radio));

        int colorBorde = (zona.totalReportes == 2) ? Color.parseColor("#FBBF24") : (zona.totalReportes <= 4 ? Color.parseColor("#F97316") : Color.parseColor("#EF4444"));
        int colorRelleno = (zona.totalReportes == 2) ? Color.argb(70, 251, 191, 36) : (zona.totalReportes <= 4 ? Color.argb(80, 249, 115, 22) : Color.argb(95, 239, 68, 68));

        zona.perimetroVisual.getOutlinePaint().setColor(colorBorde);
        zona.perimetroVisual.getFillPaint().setColor(colorRelleno);

        // Actualiza TODOS los pines que están dentro de este círculo para que tengan el mismo color
        for (Marker pin : zona.marcadores) {
            cambiarColorPin(pin, colorBorde);
        }
    }

    private Marker agregarMarcador(GeoPoint punto, String tipoCrimen, String horario, String descripcion) {
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
        return marcador;
    }

    private void borrarDirectorio(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] hijos = dir.list();
            if (hijos != null) for (String hijo : hijos) borrarDirectorio(new File(dir, hijo));
            dir.delete();
        }
    }

    @Override
    protected void onResume() { super.onResume(); if (mapa != null) mapa.onResume(); }

    @Override
    protected void onPause() { super.onPause(); if (mapa != null) mapa.onPause(); }
}