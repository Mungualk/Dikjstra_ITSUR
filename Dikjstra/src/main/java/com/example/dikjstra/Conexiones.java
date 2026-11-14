package com.example.dikjstra;

public final class Conexiones
{
    private Conexiones() {}

    public static final String[][] TODAS =

            /**
             *   Edificios: A Biblioteca | B Vinculación | C Estacionamiento | D Auditorio abandonado
             *              E Edificio B | F Edificio A | G Edificio E | H TICS | I Cafetería
             *              J Palapas | K Entrada principal | L Cabaña | M Cancha de Fútbol
             *              N Dirección | O Auditorio | P Polis | Q Basket
             *   Puntos de ruta: R=ruta 1, S=ruta 2, T=ruta 3, U=ruta 4, V=ruta 5,
             *              W=ruta 6, X=ruta 7, Y=ruta 8, Z=ruta 9, LL=ruta 10
             */
            {
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