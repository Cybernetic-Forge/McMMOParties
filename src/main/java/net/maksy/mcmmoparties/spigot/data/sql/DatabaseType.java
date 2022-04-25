package net.maksy.mcmmoparties.spigot.data.sql;

public enum DatabaseType {
    MYSQL("jdbc:mysql:", "MySQL"),
    MARIADB("jdbc:mysql:", "MariaDB (rn MySQL..)"),
    LOCALE(null, "Locale");

    private final String jdbcURL;
    private final String name;

    DatabaseType(String jdbcURL, String name) {
        this.jdbcURL = jdbcURL;
        this.name = name;
    }

    public String getJdbcUrl() { return jdbcURL; }

    public String getName() { return name; }
}
