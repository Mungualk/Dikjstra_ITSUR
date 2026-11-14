package com.example.dikjstra.algoritmo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class Dijkstra
{
    private final Grafo grafo;

    public Dijkstra(Grafo grafo)
    {
        if (grafo == null)
        {
            throw new IllegalArgumentException("El grafo no puede ser null");
        }
        this.grafo = grafo;
    }

    /** Entrada de la cola de prioridad: un nodo y la distancia con la que se encontró. */
    private record Estado(Nodo nodo, double distancia) {}

    public ResRuta calcular(Nodo origen, Nodo destino)
    {
        if (origen == null || destino == null
                || !grafo.contieneNodo(origen) || !grafo.contieneNodo(destino))
        {
            return ResRuta.sin_Camino();
        }

        Map<Nodo, Double> distancias = new HashMap<>();
        Map<Nodo, Nodo> anteriores = new HashMap<>();
        PriorityQueue<Estado> cola =
                new PriorityQueue<>(Comparator.comparingDouble(Estado::distancia));

        for (Nodo nodo : grafo.getNodos())
        {
            distancias.put(nodo, Double.POSITIVE_INFINITY);
        }
        distancias.put(origen, 0.0);
        cola.add(new Estado(origen, 0.0));

        while (!cola.isEmpty())
        {
            Estado actual = cola.poll();
            Nodo nodo = actual.nodo();
            double distancia = actual.distancia();

            // Entrada vieja: ya se encontró un camino mejor a este nodo
            if (distancia > distancias.get(nodo))
            {
                continue;
            }

            if (nodo.equals(destino))
            {
                break;
            }

            for (Map.Entry<Nodo, Double> arista : grafo.getVecinos(nodo).entrySet())
            {
                Nodo vecino = arista.getKey();
                double nuevaDistancia = distancia + arista.getValue();

                if (nuevaDistancia < distancias.get(vecino))
                {
                    distancias.put(vecino, nuevaDistancia);
                    anteriores.put(vecino, nodo);
                    cola.add(new Estado(vecino, nuevaDistancia));
                }
            }
        }

        if (Double.isInfinite(distancias.get(destino)))
        {
            return ResRuta.sin_Camino();
        }

        // Se reconstruye de atrás hacia adelante y luego se invierte
        List<Nodo> camino = new ArrayList<>();
        for (Nodo n = destino; n != null; n = anteriores.get(n))
        {
            camino.add(n);
        }
        Collections.reverse(camino);

        return new ResRuta(camino, distancias.get(destino));
    }
}