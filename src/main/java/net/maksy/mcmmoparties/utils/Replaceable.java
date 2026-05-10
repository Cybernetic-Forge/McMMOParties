package net.maksy.mcmmoparties.utils;

public class Replaceable {

    private final String K;
    private final String V;

    public Replaceable(String from, String to) {
        this.K = from;
        this.V = to;
    }

    public String getK() {
        return K;
    }

    public String getV() {
        return V;
    }
}
