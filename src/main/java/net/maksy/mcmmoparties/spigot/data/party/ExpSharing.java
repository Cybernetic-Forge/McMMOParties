package net.maksy.mcmmoparties.spigot.data.party;

public class ExpSharing {

    private double percent;
    private int radius;

    public ExpSharing(double percent, int radius) {
        this.percent = percent;
        this.radius = radius;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}
