package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "tasks")
public class Task {
    @Id
    @NotNull(message = "ID cannot be null")
    private String id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Command is required")
    private String command;

    private List<TaskExecution> taskExecutions;

    // Default constructor
    public Task() {
        this.taskExecutions = new ArrayList<>(); // Ensure it's initialized
    }

    // Constructor with all fields
    public Task(String id, String name, String owner, String command) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.command = command;
        this.taskExecutions = new ArrayList<>(); // Ensure executions are initialized
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public List<TaskExecution> getTaskExecutions() { return taskExecutions; }
    public void setTaskExecutions(List<TaskExecution> taskExecutions) { this.taskExecutions = taskExecutions; }
    
    public void addTaskExecution(TaskExecution execution) {
        this.taskExecutions.add(execution);
    }
}
