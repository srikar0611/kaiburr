package com.example.demo.controllers;

import com.example.demo.models.Task;
import com.example.demo.services.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    @Autowired
    private TaskService taskService;

    // Get all tasks
    @GetMapping
    public List<Task> getAllTasks() {
        return taskService.getAllTasks();
    }

    // Get a task by ID
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        return taskService.getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ✅ Changed to POST for creating a task
    @PostMapping("/create")
    public ResponseEntity<?> createTask(@Valid @RequestBody Task task) {
        try {
            Task createdTask = taskService.createTask(task);
            return ResponseEntity.status(201).body(createdTask);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Delete a task by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTask(@PathVariable String id) {
        boolean deleted = taskService.deleteTask(id);
        if (deleted) {
            return ResponseEntity.ok("Task deleted successfully");
        } else {
            return ResponseEntity.status(404).body("Task not found");
        }
    }

    // Search tasks by name
    @GetMapping("/search")
    public List<Task> findTasksByName(@RequestParam String name) {
        return taskService.findTasksByName(name);
    }

    // ✅ Kept PUT for executing a task
    @PutMapping("/{id}/execute")
    public ResponseEntity<String> executeTask(@PathVariable String id) {
        String result = taskService.executeTask(id);
        if ("Task not found!".equals(result)) {
            return ResponseEntity.status(404).body(result);
        } else if ("Command is not allowed!".equals(result)) {
            return ResponseEntity.status(400).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
