package com.example.dikjstra;

/**
 * Qué nodos están unidos por un camino. Cada par es {idA, idB}.
 * Las conexiones son bidireccionales: basta con escribirlas una vez.
 *
 * BORRADOR hecho a partir de las coordenadas. Se verifica sobre el mapa
 * con el modo de depuración (paso 7) y se corrige aquí.
 */
public final class Conexiones
{
    private Conexiones() {}

    public static final String[][] TODAS = {
            // Zona de entrada y estacionamiento
            {"K", "P"}, {"P", "C"}, {"C", "W"},
            {"K", "T"}, {"K", "A"}, {"A", "T"},

            // Andador horizontal y zona izquierda
            {"W", "T"}, {"T", "J"}, {"J", "B"},
            {"B", "O"}, {"B", "N"}, {"N", "L"}, {"L", "X"},

            // Centro
            {"T", "F"}, {"W", "V"}, {"V", "E"}, {"V", "S"},
            {"E", "Y"}, {"Y", "LL"}, {"Y", "M"},
            {"S", "F"}, {"S", "Z"}, {"S", "R"}, {"S", "LL"},
            {"F", "Z"}, {"Z", "X"}, {"Z", "R"}, {"Z", "G"},

            // Zona inferior
            {"LL", "R"}, {"LL", "H"}, {"R", "H"}, {"R", "G"}, {"R", "I"},
            {"H", "I"}, {"G", "X"}, {"G", "U"}, {"I", "U"},
            {"U", "Q"}, {"Q", "D"}, {"D", "X"}
    };
}