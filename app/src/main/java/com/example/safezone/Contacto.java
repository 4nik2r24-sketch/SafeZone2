package com.example.safezone;

public class Contacto {
    private final String id;
    private final String nombre;
    private final String telefono;

    public Contacto(String id, String nombre, String telefono) {
        this.id = id;
        this.nombre = nombre;
        this.telefono = telefono;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
}