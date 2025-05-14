package com.example;

import io.vertx.core.Future;
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
                        .end(new JsonObject().put("message", "user registered successfully").encode());
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
                        .put("created_at", row.getLocalDateTime("created_at").toString());
                    projects.add(project);  // Add each project to the JsonArray
                });
    
                JsonObject response = new JsonObject();
                System.out.println("Sending response: " + response.encodePrettily());

                response.put("projects", projects);  // Put the array into the response JSON
    
                // Log the response before sending it
                
                routingContext.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(response.encode());  // Send the actual projects response
                    
                    System.out.println("✅ Response sent successfully.");

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
     

    public void getProjectById(RoutingContext routingContext) {
        String projectId = routingContext.request().getParam("id");
    
        if (projectId == null) {
            routingContext.response().setStatusCode(400).end("Project ID is required.");
            return;
        }
    
        String sql = "SELECT id, name, description, status, created_at FROM projects WHERE id = $1";
        client.preparedQuery(sql)
            .execute(Tuple.of(Integer.parseInt(projectId)))
            .onSuccess(rows -> {
                if (rows.rowCount() > 0) {
                    var row = rows.iterator().next();
                    JsonObject project = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("name", row.getString("name"))
                        .put("description", row.getString("description"))
                        .put("status", row.getString("status"))
                        .put("created_at", row.getLocalDateTime("created_at").toString());
                    
                    routingContext.response()
                        .putHeader("Content-Type", "application/json")
                        .end(project.encodePrettily());  // Send response here
                    
                    System.out.println("📤 Response sent successfully!");
                } else {
                    routingContext.response().setStatusCode(404).end("Project not found.");
                }
            })
            .onFailure(err -> {
                err.printStackTrace();
                routingContext.response().setStatusCode(500).end("Error fetching project: " + err.getMessage());
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

    public void deleteProject(RoutingContext routingContext) {
        String projectId = routingContext.request().getParam("id");
        
        System.out.println("🔹 Received project ID: " + projectId);

        if (projectId == null) {
            System.out.println("❌ Project ID is missing.");
            routingContext.response().setStatusCode(400).end("Project ID is required.");
            return;
        }
    
        // Check if the project exists before deleting
        String checkSql = "SELECT id FROM projects WHERE id = $1";
        String deleteSql = "DELETE FROM projects WHERE id = $1";
    
        client.preparedQuery(checkSql).execute(Tuple.of(Integer.parseInt(projectId)))
            .onSuccess(rows -> {
                if (rows.rowCount() == 0) {
                    System.out.println("⚠️ Project with ID " + projectId + " not found.");
                    routingContext.response().setStatusCode(404).end("Project not found.");
                    return;
                }
    
                // Proceed with deletion if the project exists
                client.preparedQuery(deleteSql).execute(Tuple.of(Integer.parseInt(projectId)))
                    .onSuccess(res -> {
                        System.out.println("✅ Project with ID " + projectId + " deleted.");
                        routingContext.response().setStatusCode(200).end("Project deleted successfully.");
                    })
                    .onFailure(err -> {
                        System.out.println("❌ Failed to delete project: " + err.getMessage());
                        err.printStackTrace();
                        routingContext.response().setStatusCode(500).end("Failed to delete project.");
                    });
            })
            .onFailure(err -> {
                System.out.println("❌ Failed to check if project exists: " + err.getMessage());
                err.printStackTrace();
                routingContext.response().setStatusCode(500).end("Error: " + err.getMessage());
            });
    }
    

    // Helper method to validate the project status
    private boolean isValidStatus(String status) {
        // Define allowed statuses
        List<String> validStatuses = Arrays.asList("New", "In Progress", "Completed", "On Hold");
        return validStatuses.contains(status);
    }
    public Future<Void> updateProjectStatus(int projectId, String status) {
        String sql = "UPDATE projects SET status = ? WHERE id = ?";
        return client
            .preparedQuery(sql)
            .execute(Tuple.of(status, projectId))
            .mapEmpty();
    }
    
}
