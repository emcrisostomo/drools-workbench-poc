package com.example.droolsclientpoc.dtos;

public class Permiso implements java.io.Serializable
{
    static final long serialVersionUID = 1L;

    private int name;

    public Permiso()
    {
    }

    public Permiso(int name)
    {
        this.name = name;
    }

    public int getName()
    {
        return this.name;
    }

    public void setName(int name)
    {
        this.name = name;
    }
}