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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SafeZoneMain";

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

        File cacheOsm = new File(getCacheDir(), "osmdroid");
        borrarDirectorio(cacheOsm);

        Configuration.getInstance().setUserAgentValue("SafeZone_Iquique_V4/1.0 (contacto@safezone.cl)");
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

        txtBuscar.setThreshold(1);
        txtBuscar.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        if (cardBuscador != null) {
            txtBuscar.setDropDownAnchor(cardBuscador.getId());
        }

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

                if (tareaBusqueda != null) {
                    handlerDebounce.removeCallbacks(tareaBusqueda);
                }

                if (s != null && s.toString().trim().length() >= 2) {
                    String query = s.toString().trim();
                    tareaBusqueda = () -> ejecutarBusquedaEnSegundoPlano(query);
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
                txtBuscar.setText("");
                txtBuscar.clearFocus();
                direccionAdapter.actualizarDatos(new ArrayList<>());

                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(txtBuscar.getWindowToken(), 0);

                GeoPoint destino = new GeoPoint(dir.getLatitud(), dir.getLongitud());
                mapa.getController().animateTo(destino);
                mapa.getController().setZoom(17.5);

                if (marcadorLugarBuscado != null) {
                    mapa.getOverlays().remove(marcadorLugarBuscado);
                }
                marcadorLugarBuscado = new Marker(mapa);
                marcadorLugarBuscado.setPosition(destino);
                marcadorLugarBuscado.setTitle("Destino Consultado");
                marcadorLugarBuscado.setSnippet(dir.getNombre());
                marcadorLugarBuscado.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                if (marcadorLugarBuscado.getIcon() != null) {
                    marcadorLugarBuscado.getIcon().mutate().setColorFilter(Color.parseColor("#38BDF8"), PorterDuff.Mode.SRC_IN);
                }

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
        mapa.invalidate();
    }

    private void ejecutarBusquedaEnSegundoPlano(String consulta) {
        ejecutorRed.execute(() -> {
            List<ResultadoDireccion> listaSugerencias = new ArrayList<>();
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                String queryCod = URLEncoder.encode(consulta, "UTF-8");
                String urlStr = "https://photon.komoot.io/api/?q=" + queryCod
                        + "&lat=-20.2155&lon=-70.1513&limit=7&lang=es";

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "SafeZoneApp/1.0");

                if (conn.getResponseCode() == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = reader.readLine()) != null) sb.append(linea);

                    JSONObject geoJson = new JSONObject(sb.toString());
                    JSONArray features = geoJson.optJSONArray("features");

                    if (features != null) {
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject feat = features.getJSONObject(i);
                            JSONObject prop = feat.getJSONObject("properties");
                            JSONArray coord = feat.getJSONObject("geometry").getJSONArray("coordinates");

                            double lon = coord.getDouble(0);
                            double lat = coord.getDouble(1);

                            String nombre = prop.optString("name", "").trim();
                            String calle = prop.optString("street", "").trim();
                            String ciudad = prop.optString("city", prop.optString("locality", "")).trim();

                            String titulo = !calle.isEmpty() ? calle : (!nombre.isEmpty() ? nombre : consulta);
                            String finalLabel = !ciudad.isEmpty() ? titulo + ", " + ciudad : titulo;

                            listaSugerencias.add(new ResultadoDireccion(finalLabel, lat, lon));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {}
            }

            runOnUiThread(() -> {
                direccionAdapter.actualizarDatos(listaSugerencias);
                if (!listaSugerencias.isEmpty() && txtBuscar.hasFocus()) {
                    txtBuscar.showDropDown();
                }
            });
        });
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

        int incidentesCercanos = 0;
        Incidente ultimoIncidente = null;
        for (Incidente inc : listaIncidentes) {
            GeoPoint posIncidente = new GeoPoint(inc.getLatitud(), inc.getLongitud());
            if (posIncidente.distanceToAsDouble(puntoConsultado) <= 300.0) {
                incidentesCercanos++;
                ultimoIncidente = inc;
            }
        }

        String encabezado = nombreLugar.contains(",") ? nombreLugar.split(",")[0].trim() : nombreLugar;
        tvTituloSector.setText("Ubicación: " + (encabezado.length() > 30 ? encabezado.substring(0, 30) + "..." : encabezado));

        if (zonaEncontrada != null) {
            int total = zonaEncontrada.totalReportes;
            if (total == 1) {
                tvTipoCrimen.setText("🟢 PRECAUCIÓN: 1 Reporte Registrado");
                tvHorario.setText("Nivel de Riesgo: Bajo / Alerta Inicial");
            } else if (total <= 4) {
                tvTipoCrimen.setText("🟠 PELIGRO: " + total + " Reportes Acumulados");
                tvHorario.setText("Nivel de Riesgo: Medio / Reincidente");
            } else {
                tvTipoCrimen.setText("🔴 ZONA CRÍTICA: " + total + " Delitos Acumulados");
                tvHorario.setText("Nivel de Riesgo: Extremo");
            }

            if (ultimoIncidente != null) {
                tvDescripcion.setText("Último hecho: " + ultimoIncidente.getTipoCrimen() +
                        " (" + ultimoIncidente.getHorario() + "). " + ultimoIncidente.getDescripcionDelincuente());
            } else {
                tvDescripcion.setText("Múltiples incidentes activos en este cuadrante.");
            }

        } else if (incidentesCercanos > 0) {
            tvTipoCrimen.setText("⚠️ PRECAUCIÓN: " + incidentesCercanos + " Incidente(s) Cercano(s)");
            tvHorario.setText("Riesgo leve en los alrededores");
            if (ultimoIncidente != null) {
                tvDescripcion.setText("A menos de 300m: " + ultimoIncidente.getTipoCrimen() +
                        " (" + ultimoIncidente.getHorario() + "). " + ultimoIncidente.getDescripcionDelincuente());
            }
        } else {
            tvTipoCrimen.setText("🛡️ SECTOR SEGURO / SIN REPORTES");
            tvHorario.setText("Estado: Despejado");
            tvDescripcion.setText("No se registran incidentes en un radio de 300 metros.");
        }

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
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
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return lista;
    }

    private void ejecutarProtocoloSOS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS}, 200);
            return;
        }

        List<Contacto> contactos = obtenerContactosEmergencia();
        if (contactos.isEmpty()) {
            Toast.makeText(this, "Agrega tus contactos primero.", Toast.LENGTH_LONG).show();
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

        // LÓGICA ADAPTATIVA: Si hay 2 o más contactos, el SMS va al segundo. Si solo hay 1, va a ese único contacto.
        int indiceSMS = (contactos.size() >= 2) ? 1 : 0;
        String telefonoSMS = contactos.get(indiceSMS).getTelefono();

        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> partesMensaje = smsManager.divideMessage(mensajeSMS);
            smsManager.sendMultipartTextMessage(telefonoSMS, null, partesMensaje, null, null);
            Toast.makeText(this, "SMS despachado a " + contactos.get(indiceSMS).getNombre(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        // LLAMADA: Siempre va al Contacto 1, ejecutándose 1.5 segundos después del SMS para evitar colisión de antena
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                String telefonoLlamada = contactos.get(0).getTelefono();
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + telefonoLlamada));
                startActivity(callIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1500);
    }

    private void borrarDirectorio(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] hijos = dir.list();
            if (hijos != null) for (String hijo : hijos) borrarDirectorio(new File(dir, hijo));
            dir.delete();
        }
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
            crearNuevaZonaVisual(nuevoPunto);
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
}