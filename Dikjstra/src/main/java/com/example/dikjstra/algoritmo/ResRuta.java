package com.example.dikjstra.algoritmo;

import java.util.List;

public record ResRuta(List<Nodo> camino, double distancia)
{
    public ResRuta
    {
        camino = List.copyOf(camino);
    }

    public static ResRuta sin_Camino()
    {
        return new ResRuta(List.of(), -1);
    }

    public boolean hay_Camino()
    {
        return !camino.isEmpty();
    }
}