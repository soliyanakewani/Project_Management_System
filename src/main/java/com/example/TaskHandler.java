package com.example;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class TaskHandler {
    private final PgPool client;

    public TaskHandler(PgPool client) {
        this.client = client;
    }



   public void createTask(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        Integer assignedTo = body.getValue("assigned_to") != null ? body.getInteger("assigned_to") : null;

        client.preparedQuery("INSERT INTO tasks (project_id, name, description, status, assigned_to, created_at) VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP)")
            .execute(Tuple.of(
                body.getInteger("project_id"),
                body.getString("name"),
                body.getString("description"),
                body.getString("status"),
                assignedTo
            ), ar -> {
                if (ar.succeeded() && ar.result().size() > 0) {
                    int taskId = ar.result().iterator().next().getInteger("id"); // Retrieve generated task ID
                    ctx.response()
                    // .putHeader("Content-Type", "application/json")
                    .setStatusCode(201)
                    .end(new JsonObject().put("message", "Task created")
                    .put("id", taskId).encode());
 
                }  else {
                    ctx.response().setStatusCode(500).end("Failed to create task: " + ar.cause().getMessage());
                }
            });
    }

   public void getTasksByProject(RoutingContext ctx) {
    int projectId = Integer.parseInt(ctx.pathParam("projectId"));

    client.preparedQuery("SELECT * FROM tasks WHERE project_id = $1")
        .execute(Tuple.of(projectId), ar -> {
            if (ar.succeeded()) {
                JsonArray tasksArray = new JsonArray();
                ar.result().forEach(row -> {
                    JsonObject task = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("project_id", row.getInteger("project_id"))
                        .put("name", row.getString("name"))
                        .put("description", row.getString("description"))
                        .put("status", row.getString("status"))
                        .put("assigned_to", row.getInteger("assigned_to"))
                        .put("created_at", row.getLocalDateTime("created_at").toString()); 

                    tasksArray.add(task);
                });

                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(tasksArray.encode());
            } else {
                ctx.response().setStatusCode(500)
                    .end("Failed to fetch tasks: " + ar.cause().getMessage());
            }
        });
}


   public void updateTask(RoutingContext ctx) {
    int taskId = Integer.parseInt(ctx.pathParam("id"));
    JsonObject body = ctx.body().asJsonObject();

    // Fetch existing task details first
    String selectQuery = "SELECT name, description, status, assigned_to FROM tasks WHERE id = $1";
    client.preparedQuery(selectQuery).execute(Tuple.of(taskId), res -> {
        if (res.succeeded() && res.result().size() > 0) {
            Row row = res.result().iterator().next();

            // Keep existing values if not provided in the request
            String name = body.getString("name", row.getString("name"));
            String description = body.getString("description", row.getString("description"));
            String status = body.getString("status", row.getString("status"));
            Integer assignedTo = body.getInteger("assigned_to", row.getInteger("assigned_to"));  // 🔹 Simplified

            // Now perform the update with retained values
            String updateQuery = "UPDATE tasks SET name = $1, description = $2, status = $3, assigned_to = $4 WHERE id = $5";
            client.preparedQuery(updateQuery).execute(Tuple.of(name, description, status, assignedTo, taskId), ar -> {
                if (ar.succeeded()) {
                    ctx.response().setStatusCode(200).end("Task updated");
                } else {
                    ctx.response().setStatusCode(500).end("Failed to update task: " + ar.cause().getMessage());
                }
            });
        } else {
            ctx.response().setStatusCode(404).end("Task not found");
        }
    });
}


   public void deleteTask(RoutingContext ctx) {
        int taskId = Integer.parseInt(ctx.pathParam("id"));
        client.preparedQuery("DELETE FROM tasks WHERE id = $1")
            .execute(Tuple.of(taskId), ar -> {
                if (ar.succeeded()) {
                    ctx.response().setStatusCode(200).end("Task deleted");
                } else {
                    ctx.response().setStatusCode(500).end("Failed to delete task: " + ar.cause().getMessage());
                }
            });
    }
    public void assignUserToTask(RoutingContext ctx) {
        int taskId = Integer.parseInt(ctx.pathParam("taskId"));
        JsonObject body = ctx.body().asJsonObject();
        Integer userId = body.getInteger("userId");
        System.out.println("taskid"+taskId);
        System.out.println("user"+body);
        if (userId == null) {
            ctx.response().setStatusCode(400).end("User ID is required for assignment.");
            return;
        }
    
        client.preparedQuery("UPDATE tasks SET assigned_to = $1 WHERE id = $2")
            .execute(Tuple.of(userId, taskId), ar -> {
                if (ar.succeeded()) {
                    ctx.response().setStatusCode(200).end("Task assigned successfully");
                } else {
                    ctx.response().setStatusCode(500).end("Failed to assign task: " + ar.cause().getMessage());
                }
            });
    }
    
    public void unassignTask(RoutingContext ctx) {
        System.out.println("Unassign Task Endpoint Hit");

        int taskId = Integer.parseInt(ctx.pathParam("taskId"));
        System.out.println("Unassigning task with ID: " + taskId);

        client.preparedQuery("UPDATE tasks SET assigned_to = NULL WHERE id = $1")
            .execute(Tuple.of(taskId), ar -> {
                if (ar.succeeded()) {
                    ctx.response().setStatusCode(200).end("Task unassigned");
                } else {
                    ar.cause().printStackTrace();
                    ctx.response().setStatusCode(500).end("Failed to unassign task: " + ar.cause().getMessage());
                }
            });
    }
    
}
