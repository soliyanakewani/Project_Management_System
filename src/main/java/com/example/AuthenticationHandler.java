package com.example;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.AuthProvider;

import java.util.ArrayList;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;

public class AuthenticationHandler {

    private final Vertx vertx;
    private final JWTAuth jwtAuth;
    private final PgPool client;

    public AuthenticationHandler(Vertx vertx, JWTAuth jwtAuth, PgPool client) {
        this.vertx = vertx;
        this.jwtAuth = jwtAuth;
        this.client = client;
    }

    

    // Register a new user
    public void register(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.body().asJsonObject();
        if (requestBody == null) {
            routingContext.response().setStatusCode(400).end("Invalid JSON body.");
            return;
        }

        String username = requestBody.getString("username");
        String email = requestBody.getString("email");
        String password = requestBody.getString("password");
        String role = requestBody.getString("role");





        System.out.println("🚀 Received Registration Request:");
        System.out.println("Username: " + username);
        System.out.println("Email: " + email);
        System.out.println("Password: " + (password == null ? "NULL" : "RECEIVED"));
        System.out.println("Role: " + role);

        if (role == null) {
            role = "team member";
        }

        // Simple validation (you can extend this further)
        if (username == null || email == null  || password == null || role == null) {
            routingContext.response().setStatusCode(400).end("All fields are required.");
            return;
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println("🔑 Hashed Password: " + hashedPassword);


        // Insert new user into database (PostgreSQL)
        String sql = "INSERT INTO users (created_at, username,  email, password, role) VALUES (NOW(), $1, $2, $3, $4)";
        client.preparedQuery(sql)
            .execute(Tuple.of(username,  email, hashedPassword, role), ar -> {
                if (ar.succeeded()) {
                
                    routingContext.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject ().put("message", "registered successfully").encode());
                } else {
                    System.err.println("❌ Database Insert Error: " + ar.cause().getMessage());
                    ar.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).end("Failed to register user: " + ar.cause().getMessage());
                }
            });
    }

    // Login the user and issue JWT token
    public void login(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.body().asJsonObject();
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        if (username == null || password == null) {
            routingContext.response().setStatusCode(400).end("Username and Password are required.");
            return;
        }

        // Verify credentials against the database
        String sql = "SELECT id, username, email, role, password FROM users WHERE username = $1";
        client.preparedQuery(sql)
        .execute(Tuple.of(username), ar -> {
            if (ar.succeeded() && ar.result().rowCount() > 0) {
                JsonObject user = new JsonObject();
                ar.result().forEach(row -> {
                    user.put("id", row.getInteger("id"));
                    user.put("username", row.getString("username"));
                    user.put("email", row.getString("email"));
                    user.put("hashedPassword", row.getString("password"));
                    user.put("role", row.getString("role"));
                });

                // Verify password using BCrypt
                String hashedPassword = user.getString("hashedPassword");
                if (!BCrypt.checkpw(password, hashedPassword)) {
                    routingContext.response().setStatusCode(401).end("Invalid credentials.");
                    return;
                }

                // Issue JWT token
                String token = jwtAuth.generateToken(new JsonObject().put("username", user.getString("username")));

                routingContext.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("token", token).encodePrettily());
            } else {
                routingContext.response().setStatusCode(401).end("Invalid credentials.");
            }
        });
}
// Add this method to your AuthenticationHandler class
public void getAllUsers(RoutingContext routingContext) {
    // SQL to fetch all users
    String sql = "SELECT id, username, email, role FROM users";
    
    client.preparedQuery(sql)
        .execute()
        .onSuccess(rows -> {
            // Prepare the response as a list of users
            JsonObject response = new JsonObject();
            List<JsonObject> usersList = new ArrayList<>();

            rows.forEach(row -> {
                JsonObject user = new JsonObject()
                    .put("id", row.getInteger("id"))
                    .put("username", row.getString("username"))
                    .put("email", row.getString("email"))
                    .put("role", row.getString("role"));
                usersList.add(user);
            });

            response.put("users", usersList);

            routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encodePrettily());
        })
        .onFailure(cause -> {
            routingContext.response()
                .setStatusCode(500)
                .end("Failed to fetch users: " + cause.getMessage());
        });
}

// Add this method to your AuthenticationHandler class
public void getUserById(RoutingContext routingContext) {
    // Extract user ID from the path parameters
    String userId = routingContext.request().getParam("id");
    
    if (userId == null) {
        routingContext.response().setStatusCode(400).end("User ID is required.");
        return;
    }

    // SQL to fetch a user by ID
    String sql = "SELECT id, username, email, role FROM users WHERE id = $1";
    
    client.preparedQuery(sql)
        .execute(Tuple.of(Integer.parseInt(userId)))
        .onSuccess(rows -> {
            if (rows.rowCount() > 0) {
                JsonObject user = new JsonObject();
                rows.forEach(row -> {
                    user.put("id", row.getInteger("id"));
                    user.put("username", row.getString("username"));
                    user.put("email", row.getString("email"));
                    user.put("role", row.getString("role"));
                });

                routingContext.response()
                    .putHeader("Content-Type", "application/json")
                    .end(user.encodePrettily());
            } else {
                routingContext.response()
                    .setStatusCode(404)
                    .end("User not found.");
            }
        })
        .onFailure(cause -> {
            routingContext.response()
                .setStatusCode(500)
                .end("Failed to fetch user: " + cause.getMessage());
        });
}

}