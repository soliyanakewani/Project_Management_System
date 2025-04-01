package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.pgclient.PgPool;
import java.util.Arrays;

public class MainVerticle extends AbstractVerticle {
    private PgPool client;

    @Override
    public void start(Promise<Void> startPromise) {
        // Use DatabaseConnector to establish DB connection
        client = DatabaseConnector.connect(vertx);

        client.query("SELECT 1")
        .execute()
        .onSuccess(res -> System.out.println("✅ Database test query succeeded."))
        .onFailure(err -> System.out.println("❌ Database test query failed: " + err.getMessage()));
    
        

        // Router Setup
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // JWT Auth Setup
        JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer("supersecretkey")));


        // Authentication Routes
        AuthenticationHandler authHandler = new AuthenticationHandler(vertx, jwtAuth, client);
        router.post("/auth/register").handler(authHandler::register);
        router.post("/auth/login").handler(authHandler::login);

        router.route("/users*").handler(ctx -> {
            System.out.println("Request Headers: " + ctx.request().headers());
            JWTAuthHandler.create(jwtAuth).handle(ctx);
        });
        router.get("/users").handler(routingContext -> {
            System.out.println("Accessing /users route");
            authHandler.getAllUsers(routingContext);
        });
        router.get("/users/:id").handler(routingContext -> {
            System.out.println("Accessing /users/:id route");
            authHandler.getUserById(routingContext);
        });
        router.route("/api/*").handler(JWTAuthHandler.create(jwtAuth));

        // Define the route with role-based access
  
        // Allow both Project Managers and Team Members to access tasks
        router.get("/tasks").handler(routingContext -> {
            checkRole(routingContext, "project_manager", "team_member");
            // Task logic here...
});
ProjectHandler projectHandler = new ProjectHandler(client);
// Create a new project (admin or project manager can do this)
router.post("/projects").handler(ctx -> {
    System.out.println("✅ Route /projects POST triggered");
    projectHandler.createProject(ctx);
});



router.get("/projects").handler(ctx -> {
    System.out.println("✅ Route /projects GET triggered");
    projectHandler.getAllProjects(ctx);
});

// Get a project by ID (admin, project manager, or team member)
router.get("/projects/:id").handler(routingContext -> {
    checkRole(routingContext, "admin", "project_manager", "team_member");
    projectHandler.getProjectById(routingContext);
});

// Update a project (admin or project manager)
router.put("/projects/:id").handler(routingContext -> {
    checkRole(routingContext, "admin", "project_manager");
    projectHandler.updateProject(routingContext);
});

// Delete a project (admin or project manager)
router.delete("/projects/:id").handler(routingContext -> {
    checkRole(routingContext, "admin", "project_manager");
    projectHandler.deleteProject(routingContext);
});



        // Start HTTP Server
        vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                System.out.println("✅ HTTP server started on port 8888");
            } else {
                startPromise.fail(http.cause());
                System.out.println("❌ Failed to start HTTP server: " + http.cause().getMessage());
            }
        });
    }

    // Method to check if the user has the required role
    private void checkRole(RoutingContext routingContext, String... allowedRoles) {
        // Get the user's role from the JWT token
        String userRole = routingContext.user().principal().getString("role");
        System.out.println("user role: " + userRole);

        if (userRole == null || !Arrays.asList(allowedRoles).contains(userRole)) {
            // If the user does not have the required role, return forbidden
            routingContext.response().setStatusCode(403).end("Forbidden: Insufficient permissions");
        } else {
            // If the user has the required role, proceed with the request
            routingContext.next();
        }
    }
}
