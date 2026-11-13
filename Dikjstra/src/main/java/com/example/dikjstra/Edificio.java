package com.example.dikjstra;

/**
 * Un edificio / punto de referencia del mapa.
 * x e y están en PÍXELES DE LA IMAGEN ORIGINAL (itsur.jpeg), no de la pantalla.
 *
 * Uso:
 *   new Edificio("C", "Biblioteca", "Horario 8-18", 700, 450)
 *       .conColor("#2ecc71")
 *       .conIcono("/ICONOS/libro.png")
 *       .conTamano(90, 50);
 */
public class Edificio {
    private final String id;
    private final String nombre;
    private final String descripcion;
    private double x;
    private double y;

    private String color = null;   // null = color por defecto del CSS
    private String icono = null;   // ruta dentro de resources, ej. "/ICONOS/libro.png"
    private double ancho = 70;     // tamaño del bloque en el mapa CSS
    private double alto = 45;

    public Edificio(String id, String nombre, String descripcion, double x, double y) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.x = x;
        this.y = y;
    }

    // ---- Configuración encadenable ----
    public Edificio conColor(String colorCss) { this.color = colorCss; return this; }
    public Edificio conIcono(String ruta)     { this.icono = ruta; return this; }
    public Edificio conTamano(double ancho, double alto) {
        this.ancho = ancho;
        this.alto = alto;
        return this;
    }

    // ---- Lectura ----
    public String id() { return id; }
    public String nombre() { return nombre; }
    public String descripcion() { return descripcion; }
    public double x() { return x; }
    public double y() { return y; }
    public String color() { return color; }
    public String icono() { return icono; }
    public double ancho() { return ancho; }
    public double alto() { return alto; }

    // ---- Mover ----
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
}