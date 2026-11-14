package com.example.dikjstra;

import com.example.dikjstra.algoritmo.Dijkstra;
import com.example.dikjstra.algoritmo.Grafo;
import com.example.dikjstra.algoritmo.Nodo;
import com.example.dikjstra.algoritmo.ResRuta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RedRutas
{
    /**
     imagen original (1172 x 896).
     */
    public static final double METROS_POR_PIXEL = 0.2732;

    public record Ruta(List<String> ids, double metros) {}

    private final Grafo grafo = new Grafo();
    private final Dijkstra dijkstra = new Dijkstra(grafo);
    private final List<String> todosLosIds = new ArrayList<>();

    public RedRutas(List<Edificio> edificios, String[][] conexiones)
    {
        Map<String, Edificio> porId = new HashMap<>();

        for (Edificio e : edificios)
        {
            if (porId.put(e.id(), e) != null)
            {
                throw new IllegalArgumentException("Id de edificio repetido: " + e.id());
            }
            todosLosIds.add(e.id());
            grafo.agregarNodo(new Nodo(e.id()));
        }

        for (String[] par : conexiones)
        {
            Edificio a = porId.get(par[0]);
            Edificio b = porId.get(par[1]);
            if (a == null || b == null)
            {
                throw new IllegalArgumentException(
                        "Conexión con id inexistente: " + par[0] + " - " + par[1]);
            }

            double pixeles = Math.hypot(b.x() - a.x(), b.y() - a.y());
            grafo.crearArista(new Nodo(a.id()), new Nodo(b.id()), pixeles * METROS_POR_PIXEL);
        }
    }

    public Optional<Ruta> calcular(String idOrigen, String idDestino)
    {
        ResRuta r = dijkstra.calcular(new Nodo(idOrigen), new Nodo(idDestino));
        if (!r.hay_Camino())
        {
            return Optional.empty();
        }

        List<String> ids = r.camino().stream().map(Nodo::id).toList();
        return Optional.of(new Ruta(ids, r.distancia()));
    }

    public List<String> idsInalcanzables()
    {
        List<String> aislados = new ArrayList<>();
        String base = todosLosIds.get(0);
        for (String id : todosLosIds)
        {
            if (calcular(base, id).isEmpty())
            {
                aislados.add(id);
            }
        }
        return aislados;
    }
}