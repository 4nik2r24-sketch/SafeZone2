package com.example.safezone;

public class Incidente {
    private final String idDelito;
    private final double latitud;
    private final double longitud;
    private final String tipoCrimen;
    private final String descripcionDelincuente;
    private final String horario;

    public Incidente(String idDelito, double latitud, double longitud, String tipoCrimen, String descripcionDelincuente, String horario) {
        this.idDelito = idDelito;
        this.latitud = latitud;
        this.longitud = longitud;
        this.tipoCrimen = tipoCrimen;
        this.descripcionDelincuente = descripcionDelincuente;
        this.horario = horario;
    }

        public String getIdDelito() { return idDelito; }
        public double getLatitud() { return latitud; }
        public double getLongitud() { return longitud; }
        public String getTipoCrimen() { return tipoCrimen; }
        public String getDescripcionDelincuente() { return descripcionDelincuente; }
        public String getHorario() { return horario; }
}

