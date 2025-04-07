package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
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
        router.route().handler(CorsHandler.create("*").allowedMethod(io.vertx.core.http.HttpMethod.GET).allowedMethod(io.vertx.core.http.HttpMethod.POST).allowedMethod(io.vertx.core.http.HttpMethod.PUT).allowedMethod(io.vertx.core.http.HttpMethod.DELETE).allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS));

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
        router.get("/users/:role").handler(routingContext -> {
            System.out.println("Accessing /users/:role route");
            authHandler.getTeamMembers(routingContext);
        });
        router.route("/api/*").handler(JWTAuthHandler.create(jwtAuth));

        // add the task handler
       // Initialize TaskHandler
TaskHandler taskHandler = new TaskHandler(client);

// Define Task Routes Directly
router.post("/tasks").handler(ctx -> {
    System.out.println("✅ Route /tasks POST triggered");
    taskHandler.createTask(ctx);
});

router.get("/tasks/:projectId").handler(ctx -> {
    System.out.println("✅ Route /tasks/:projectId GET triggered");
    taskHandler.getTasksByProject(ctx);
});

router.put("/tasks/:id").handler(ctx -> {
    System.out.println("✅ Route /tasks/:id PUT triggered");
    taskHandler.updateTask(ctx);
});

router.delete("/tasks/:id").handler(ctx -> {
    System.out.println("✅ Route /tasks/:id DELETE triggered");
    taskHandler.deleteTask(ctx);
});

router.put("/tasks/:id").handler(ctx -> {
    System.out.println("✅ Route /tasks/:id UNASSIGN triggered");
    taskHandler.unassignTask(ctx);
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

// Get a project by ID  
router.get("/projects/:id").handler(ctx -> {
    System.out.println("✅ Route /projects/:id GET triggered");
    projectHandler.getProjectById(ctx);
});

// Update a project  
router.put("/projects/:id").handler(ctx -> {
    System.out.println("✅ Route /projects/:id PUT triggered");
    projectHandler.updateProject(ctx);
});


// Delete a project (admin or project manager)
router.delete("/projects/:id").handler(ctx -> {
    System.out.println("✅ Route /projects DELETE triggered");
    projectHandler.deleteProject(ctx);
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
