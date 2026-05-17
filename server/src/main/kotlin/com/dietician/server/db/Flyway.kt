package com.dietician.server.db

import org.flywaydb.core.Flyway

fun runMigrations(jdbcUrl: String, user: String, password: String): Int {
    val flyway = Flyway.configure()
        .dataSource(jdbcUrl, user, password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .load()
    return flyway.migrate().migrationsExecuted
}
