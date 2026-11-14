package com.example.dikjstra.algoritmo;

import java.util.Objects;

public class Nodo
{
    private String id;

    public Nodo(String id_p)
    {
        this.id = id_p;
    }

    public String getId()
    {
        return this.id;
    }

    public void setId(String id_p)
    {
        this.id = id_p;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Nodo)) return false;

        Nodo nodo = (Nodo) o;
        return Objects.equals(getId(), nodo.getId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getId());
    }

    @Override
    public String toString()
    {
        String s = String.format("Nodo{ id=%s }", this.id);
        return s;
    }

}
