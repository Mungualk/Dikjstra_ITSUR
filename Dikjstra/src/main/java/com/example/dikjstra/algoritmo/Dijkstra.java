package com.example.dikjstra.algoritmo;
import java.util.*;

public class Dijkstra
{
    private Grafo grafo;

    public Dijkstra(Grafo grafo)
    {
        if (grafo == null)
        {
            throw new IllegalArgumentException("El grafo no puede ser null");
        }

        this.grafo = grafo;
    }

    private static class Estado
    {
        Nodo nodo;
        long dist;

        public Estado(Nodo nodo, long dist)
        {
            this.nodo = nodo;
            this.dist = dist;
        }
    }

    public static class calculo_ruta
    {
        private List<Nodo> cam;
        private long dist;

        public calculo_ruta(List<Nodo> cam, long dist)
        {
            this.cam = Collections.unmodifiableList(
                    new ArrayList<>(cam)
            );
            this.dist = dist;
        }

        public List<Nodo> getCamino()
        {
            return cam;
        }

        public long getDist()
        {
            return this.dist;
        }

        public boolean hayCamino()
        {
            return !cam.isEmpty();
        }
    }

    // Algoritmo
    public calculo_ruta calcular(Nodo in, Nodo fin)
    {
        if (in == null || fin == null || !grafo.getNodos().contains(in) || !grafo.getNodos().contains(fin))
        {
            return new calculo_ruta(Collections.emptyList(), -1);
        }

        Map<Nodo, Long> distancias_map = new HashMap<>();
        Map<Nodo, Nodo> nodos_anteriores = new HashMap<>();

        PriorityQueue<Estado> p_c = new PriorityQueue<>(Comparator.comparingLong(estado -> estado.dist));

        for (Nodo nodo : grafo.getNodos())
        {
            distancias_map.put(nodo, Long.MAX_VALUE);
        }

        distancias_map.put(in, 0L);

        p_c.add(new Estado(in, 0));


        while (!p_c.isEmpty())
        {
            Estado estadoActual = p_c.poll();

            Nodo act = estadoActual.nodo;
            long disAct = estadoActual.dist;


            if (disAct != distancias_map.get(act))
            {
                continue;
            }

            if (act.equals(fin))
            {
                break;
            }

            for (Map.Entry<Nodo, Integer> arista : grafo.getVecinos(act).entrySet())
            {
                Nodo vecino = arista.getKey();
                int peso = arista.getValue();

                long nDis = disAct + peso;

                if (nDis < distancias_map.get(vecino))
                {
                    distancias_map.put(vecino, nDis);

                    nodos_anteriores.put(vecino, act);

                    p_c.add(new Estado(vecino, nDis));
                }
            }
        }

        if (distancias_map.get(fin) == Long.MAX_VALUE)
        {
            return new calculo_ruta(Collections.emptyList(), -1);
        }

        Stack<Nodo> ruta = new Stack<>();

        Nodo actual = fin;

        while (actual != null)
        {
            ruta.push(actual);

            if (actual.equals(in))
            {
                break;
            }

            actual = nodos_anteriores.get(actual);
        }

        List<Nodo> camino = new ArrayList<>(ruta);

        Collections.reverse(camino);

        return new calculo_ruta(camino, distancias_map.get(fin));
    }
}

