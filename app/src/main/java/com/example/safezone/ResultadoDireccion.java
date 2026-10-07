package com.example.safezone;

public class ResultadoDireccion {
    private final String nombre;
    private final double latitud;
    private final double longitud;

    public ResultadoDireccion(String nombre, double latitud, double longitud) {
        this.nombre = nombre;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getNombre() { return nombre; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }

    @Override
    public String toString() {
        return nombre;
    }
}