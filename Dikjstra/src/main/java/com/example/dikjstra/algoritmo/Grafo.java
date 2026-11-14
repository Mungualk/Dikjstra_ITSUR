package com.example.dikjstra.algoritmo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Grafo
{
    private final Map<Nodo, Map<Nodo, Double>> adyacencia = new HashMap<>();

    public void agregarNodo(Nodo nodo)
    {
        if (nodo == null)
        {
            return;
        }
        adyacencia.putIfAbsent(nodo, new HashMap<>());
    }

    public void crearArista(Nodo a, Nodo b, double peso)
    {
        if (a == null || b == null)
        {
            throw new IllegalArgumentException("Los nodos de una arista no pueden ser null");
        }
        if (a.equals(b))
        {
            throw new IllegalArgumentException("Un nodo no puede conectarse consigo mismo: " + a);
        }
        if (!(peso >= 0))
        {
            throw new IllegalArgumentException("El peso debe ser mayor o igual a 0: " + peso);
        }

        agregarNodo(a);
        agregarNodo(b);
        adyacencia.get(a).put(b, peso);
        adyacencia.get(b).put(a, peso);
    }

    public boolean contieneNodo(Nodo nodo)
    {
        return adyacencia.containsKey(nodo);
    }

    public Map<Nodo, Double> getVecinos(Nodo nodo)
    {
        Map<Nodo, Double> vecinos = adyacencia.get(nodo);
        if (vecinos == null)
        {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(vecinos);
    }

    public Set<Nodo> getNodos()
    {
        return Collections.unmodifiableSet(adyacencia.keySet());
    }

    public double getPeso(Nodo a, Nodo b)
    {
        Double peso = getVecinos(a).get(b);
        return peso == null ? -1 : peso;
    }
}