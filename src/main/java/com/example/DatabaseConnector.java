package com.example;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.pgclient.PgConnectOptions;

public class DatabaseConnector {
    public static PgPool connect(Vertx vertx) {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("project_management")
            .setUser("postgres")  
            .setPassword("password")
            .setSsl(false);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        PgPool client = PgPool.pool(vertx, connectOptions, poolOptions);
        
        // Test Connection
        client.getConnection(ar -> {
            if (ar.succeeded()) {
                System.out.println("✅ Database connected successfully!");
                ar.result().close();
            } else {
                System.out.println("❌ Failed to connect to the database: " + ar.cause().getMessage());
            }
        });

        return client;
    }
}
