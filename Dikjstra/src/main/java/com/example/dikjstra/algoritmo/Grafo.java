package com.example.dikjstra.algoritmo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Grafo
{
    private Map<Nodo, Map<Nodo, Integer>> ad_l;

    public Grafo()
    {
        this.ad_l = new HashMap<>();
    }

    public void pucharNodo(Nodo nodo)
    {
        if (!(nodo == null)) {
            if (!this.ad_l.containsKey(nodo)) {
                ad_l.put(nodo, new HashMap<>());
            }
        }
    }

    public void crearArista(Nodo nodo_1, Nodo nodo_2, int peso)
    {
        if(!(nodo_1 == null || nodo_2 == null))
        {
            if((!nodo_1.equals(nodo_2)) && peso >= 0)
            {
                if(!this.ad_l.containsKey(nodo_1))
                {
                    pucharNodo(nodo_1);
                }
                if(!this.ad_l.containsKey(nodo_2))
                {
                    pucharNodo(nodo_2);
                }
                ad_l.get(nodo_1).put(nodo_2, peso);
                ad_l.get(nodo_2).put(nodo_1, peso);
            }

        }
    }

    public Map<Nodo, Integer> getVecinos(Nodo nodo)
    {
        if(!this.ad_l.containsKey(nodo))
        {
            return null;
        }
        else
        {
            return this.ad_l.get(nodo);
        }
    }

    public Set<Nodo> getNodos ()
    {
        Set<Nodo> set = ad_l.keySet();
        return set;
    }

    public int getPeso(Nodo nodo_1, Nodo nodo_2)
    {
        Map<Nodo, Integer> vecinos = getVecinos(nodo_1);

        if (vecinos.containsKey(nodo_2))
        {
            return vecinos.get(nodo_2);
        }

        return -1;
    }



}
