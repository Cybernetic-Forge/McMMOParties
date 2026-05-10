package net.maksy.mcmmoparties.utils;

import java.text.DecimalFormat;

public class Utils {

    public static boolean isNotNumber(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static float round(float value) {
        DecimalFormat df = new DecimalFormat("#.##");
        return Float.parseFloat(df.format(value));
    }
}
