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

/**
 * Une el mapa (Edificio, con coordenadas en píxeles) con el algoritmo (Grafo/Dijkstra).
 * Se construye UNA vez al iniciar la app y luego se le piden rutas.
 */
public class RedRutas
{
    /**
     * Cuántos metros mide un píxel de la imagen original (1172 x 896).
     * Se obtiene midiendo la barra de escala de la captura: metros que indica / píxeles que mide.
     * PROVISIONAL: cambia este valor cuando midas la barra.
     */
    public static final double METROS_POR_PIXEL = 0.25;

    /** Resultado listo para el front: ids en orden y distancia total en metros. */
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

    /** Ruta más corta entre dos ids, o vacío si no hay camino. */
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

    /** Ids a los que NO se puede llegar desde el primer edificio. Lo ideal es que esté vacía. */
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