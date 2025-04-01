package com.example;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import io.vertx.pgclient.PgPool;
// import io.vertx.sqlclient.Tuple;
// import io.vertx.sqlclient.RowSet;

import java.util.Arrays;
import java.util.List;

public class ProjectHandler {

    private final PgPool client;

    public ProjectHandler(PgPool client) {
        this.client = client;
    }
    public void createProject(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.body().asJsonObject();
        System.out.println("🟢 Received request body: " + requestBody);
    
        if (requestBody == null) {
            System.out.println("❌ Request body is NULL");
            routingContext.response().setStatusCode(400).end("Request body is missing.");
            return;
        }
    
        if (!requestBody.containsKey("name") || !requestBody.containsKey("description")) {
            System.out.println("❌ Missing project name or description.");
            routingContext.response().setStatusCode(400).end("Missing project name or description.");
            return;
        }
    
        String name = requestBody.getString("name");
        String description = requestBody.getString("description");
        String status = requestBody.getString("status", "New"); // Default to 'New'
    
        if (!isValidStatus(status)) {
            System.out.println("❌ Invalid project status: " + status);
            routingContext.response().setStatusCode(400).end("Invalid project status.");
            return;
        }
    
        String sql = "INSERT INTO projects (name, description, status, created_at) VALUES ($1, $2, $3, NOW()) RETURNING id";
        System.out.println("⚡ Preparing to execute SQL: " + sql);
        System.out.println("🔹 Parameters: name=" + name + ", description=" + description + ", status=" + status);
    
        // 🛑 Check if `client` is null
        if (client == null) {
            System.out.println("❌ Database client is NULL!");
            routingContext.response().setStatusCode(500).end("Database connection error.");
            return;
        }
    
        client.preparedQuery(sql)
            .execute(Tuple.of(name, description, status))
            .onSuccess(rows -> {
                System.out.println("✅ Query executed successfully, row count: " + rows.rowCount());
                if (rows.rowCount() > 0) {
                    int projectId = rows.iterator().next().getInteger("id");
                    System.out.println("✅ Project created successfully with ID: " + projectId);
                    routingContext.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("project_id", projectId).encode());
                } else {
                    System.out.println("⚠️ Query succeeded but returned no rows.");
                    routingContext.response()
                        .setStatusCode(500)
                        .end("Unexpected error: No rows were returned.");
                }
            })
            .onFailure(err -> {
                System.out.println("❌ Query execution failed: " + err.getMessage());
                err.printStackTrace();
                routingContext.response()
                    .setStatusCode(500)
                    .end("Failed to create project: " + err.getMessage());
            });
    
        System.out.println("🔍 Query execution triggered.");
    }
    


    public void getAllProjects(RoutingContext routingContext) {
        String sql = "SELECT id, name, description, status, created_at FROM projects";
        
        // Log query before execution
        System.out.println("Preparing to execute SQL: " + sql);
        
        client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                // Log the size of the result set to verify data is being fetched
                System.out.println("Query executed successfully, row count: " + rows.size());
                if (rows.size() == 0) {
                    System.out.println("No projects found in the database.");
                }
    
                JsonArray projects = new JsonArray(); // Create a JsonArray to store the projects
                
                rows.forEach(row -> {
                    JsonObject project = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("name", row.getString("name"))
                        .put("description", row.getString("description"))
                        .put("status", row.getString("status"))
                        .put("created_at", row.getString("created_at"));
                    projects.add(project);  // Add each project to the JsonArray
                });
    
                JsonObject response = new JsonObject();
                response.put("projects", projects);  // Put the array into the response JSON
    
                // Log the response before sending it
                System.out.println("Sending response with projects: " + response.encodePrettily());
    
                routingContext.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encodePrettily());  // Send the actual projects response
            })
            .onFailure(cause -> {
                // Log the error details
                System.out.println("❌ Failed to fetch projects: " + cause.getMessage());
                cause.printStackTrace();  // Log the full stack trace of the error
                
                routingContext.response()
                    .setStatusCode(500)
                    .end("Failed to fetch projects: " + cause.getMessage());
            });
    }
     

    // Get project by ID
    public void getProjectById(RoutingContext routingContext) {
        String projectId = routingContext.request().getParam("id");
        if (projectId == null) {
            routingContext.response().setStatusCode(400).end("Project ID is required.");
            return;
        }

        String sql = "SELECT id, name, description, status, created_at FROM projects WHERE id = $1";
        client.preparedQuery(sql)
            .execute(Tuple.of(Integer.parseInt(projectId)), ar -> {
                if (ar.succeeded() && ar.result().rowCount() > 0) {
                    JsonObject project = ar.result().iterator().next().toJson();
                    routingContext.response()
                        .putHeader("Content-Type", "application/json")
                        .end(project.encodePrettily());
                } else {
                    routingContext.response().setStatusCode(404).end("Project not found.");
                }
            });
    }

    // Update project
    public void updateProject(RoutingContext routingContext) {
        String projectId = routingContext.request().getParam("id");
        if (projectId == null) {
            routingContext.response().setStatusCode(400).end("Project ID is required.");
            return;
        }

        JsonObject requestBody = routingContext.body().asJsonObject();
        String name = requestBody.getString("name", null);
        String description = requestBody.getString("description", null);
        String status = requestBody.getString("status", null);

        // Ensure status is valid if provided
        if (status != null && !isValidStatus(status)) {
            routingContext.response().setStatusCode(400).end("Invalid project status.");
            return;
        }

        String sql = "UPDATE projects SET name = COALESCE($1, name), description = COALESCE($2, description), status = COALESCE($3, status) WHERE id = $4";
        client.preparedQuery(sql)
            .execute(Tuple.of(name, description, status, Integer.parseInt(projectId)), ar -> {
                if (ar.succeeded()) {
                    routingContext.response().setStatusCode(200).end("Project updated successfully.");
                } else {
                    routingContext.response()
                        .setStatusCode(500)
                        .end("Failed to update project: " + ar.cause().getMessage());
                }
            });
    }

    // Delete project
    public void deleteProject(RoutingContext routingContext) {
        String projectId = routingContext.request().getParam("id");
        if (projectId == null) {
            routingContext.response().setStatusCode(400).end("Project ID is required.");
            return;
        }

        String sql = "DELETE FROM projects WHERE id = $1";
         client.preparedQuery(sql)
            .execute(Tuple.of(Integer.parseInt(projectId)), ar -> {
                if (ar.succeeded()) {
                    routingContext.response().setStatusCode(200).end("Project deleted successfully.");
                } else {
                    routingContext.response()
                        .setStatusCode(500)
                        .end("Failed to delete project: " + ar.cause().getMessage());
                }
            });
    }

    // Helper method to validate the project status
    private boolean isValidStatus(String status) {
        // Define allowed statuses
        List<String> validStatuses = Arrays.asList("New", "In Progress", "Completed", "On Hold");
        return validStatuses.contains(status);
    }
}
