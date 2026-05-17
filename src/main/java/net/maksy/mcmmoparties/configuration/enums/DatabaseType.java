package net.maksy.mcmmoparties.configuration.enums;

import lombok.Getter;

public enum DatabaseType {
    MYSQL("jdbc:mysql:", "MySQL"),
    MARIADB("jdbc:mysql:", "MariaDB (rn MySQL..)"),
    SQLITE(null, "SQLITE");

    private final String jdbcURL;
    @Getter
    private final String name;

    DatabaseType(String jdbcURL, String name) {
        this.jdbcURL = jdbcURL;
        this.name = name;
    }

    public String getJdbcUrl() { return jdbcURL; }

}
