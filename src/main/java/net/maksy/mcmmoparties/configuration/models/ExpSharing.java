package net.maksy.mcmmoparties.configuration.models;

public class ExpSharing {

    private double percent;
    private int radius;

    private boolean isScaling = false;

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

    public boolean isScaling() { return isScaling; }

    public void setScaling(boolean isScaling) { this.isScaling = isScaling; }
}
