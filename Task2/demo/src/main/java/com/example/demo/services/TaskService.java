package com.example.demo.services;

import com.example.demo.models.Task;
import com.example.demo.repositories.TaskRepository;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);
    
    @Autowired
    private TaskRepository taskRepository;
    
    // Allowed commands for security
    private static final String[] SAFE_COMMANDS = {"echo", "ls", "date", "uptime"};
    
    // Regex to detect dangerous shell characters
    private static final Pattern UNSAFE_PATTERN = Pattern.compile("[;&|><`$()]");
    
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
    
    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }
    
    public Task createTask(Task task) {
        if (!isValidCommand(task.getCommand())) {
            throw new IllegalArgumentException("Command is not allowed for security reasons.");
        }
        return taskRepository.save(task);
    }
    
    public boolean deleteTask(String id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    public List<Task> findTasksByName(String name) {
        return taskRepository.findByNameContaining(name);
    }
    
    // Validate command security
    private boolean isValidCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        if (UNSAFE_PATTERN.matcher(command).find()) {
            return false;
        }
        for (String safe : SAFE_COMMANDS) {
            if (command.startsWith(safe)) {
                return true;
            }
        }
        return false;
    }
    
    public String executeTask(String id) {
        Optional<Task> taskOptional = taskRepository.findById(id);
        if (taskOptional.isEmpty()) {
            return "Task not found!";
        }
        
        Task task = taskOptional.get();
        String command = task.getCommand();
        
        if (!isValidCommand(command)) {
            return "Command is not allowed!";
        }
        
        // Check if Kubernetes is available or fall back to local execution
        boolean kubernetesAvailable = checkKubernetesAvailability();
        if (!kubernetesAvailable) {
            logger.info("Kubernetes unavailable, falling back to local execution for command: {}", command);
            return executeTaskLocally(command);
        }
        
        try {
            // Kubernetes is available, proceed with pod creation
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();
            
            // Create unique pod name with timestamp to avoid conflicts
            String podName = "task-exec-" + id + "-" + System.currentTimeMillis();
            
            // Define a Kubernetes pod with BusyBox to execute the command
            V1Pod pod = new V1Pod()
                    .apiVersion("v1")
                    .kind("Pod")
                    .metadata(new V1ObjectMeta()
                            .name(podName)
                            .namespace("default")
                            .labels(Collections.singletonMap("app", "task-executor")))
                    .spec(new V1PodSpec()
                            .containers(Collections.singletonList(new V1Container()
                                    .name("executor")
                                    .image("busybox:latest")
                                    .command(List.of("sh", "-c", command))
                            ))
                            .restartPolicy("Never"));
                            
            // Deploy the pod with more detailed response handling
            logger.info("Attempting to create Kubernetes pod: {}", podName);
            V1Pod createdPod = api.createNamespacedPod("default", pod, null, null, null, null);
            logger.info("Pod created successfully: {}", createdPod.getMetadata().getName());
            
            return "Task execution started in Kubernetes pod: " + podName;
        } catch (Exception e) {
            String errorType = e.getClass().getName();
            String errorMsg = (e.getMessage() != null && !e.getMessage().isEmpty()) ? 
                               e.getMessage() : "No specific error message. Check if Kubernetes is properly configured.";
            
            logger.error("Failed to create Kubernetes pod. Error type: {} - Details: {}", errorType, errorMsg, e);
            
            // Fall back to local execution if Kubernetes pod creation fails
            logger.info("Attempting fallback to local execution");
            return executeTaskLocally(command);
        }
    }
    
    // Check if Kubernetes is available
    private boolean checkKubernetesAvailability() {
        try {
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();
            
            // Just try a simple API call
            api.listNamespacedPod("default", null, null, null, null, null, null, null, null, null, null);
            return true;
        } catch (Exception e) {
            String errorType = e.getClass().getName();
            String errorMsg = (e.getMessage() != null && !e.getMessage().isEmpty()) ? 
                               e.getMessage() : "No error message available";
            
            logger.warn("Kubernetes is not available. Error type: {} - Details: {}", errorType, errorMsg);
            return false;
        }
    }
    
    // Execute task locally as fallback
    private String executeTaskLocally(String command) {
        try {
            logger.info("Executing command locally: {}", command);
            Process process = Runtime.getRuntime().exec(command);
            
            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));
            
            // Capture error if any
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String error = errorReader.lines().collect(Collectors.joining("\n"));
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return "Task executed locally - Output:\n" + output;
            } else {
                return "Task executed locally but failed (exit code " + exitCode + "):\n" + error;
            }
        } catch (Exception e) {
            logger.error("Local execution failed: {}", e.getMessage(), e);
            return "Local execution failed: " + e.getMessage();
        }
    }
    
    // Helper method to check pod status
    public String checkTaskStatus(String podName) {
        try {
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();
            
            V1Pod pod = api.readNamespacedPod(podName, "default", null);
            String phase = pod.getStatus().getPhase();
            
            // Get logs if pod is completed or running
            String logs = "";
            if ("Succeeded".equals(phase) || "Running".equals(phase)) {
                try {
                    logs = api.readNamespacedPodLog(podName, "default", "executor", null, null, null, null, null, null, null, null);
                    return "Pod status: " + phase + "\nLogs:\n" + logs;
                } catch (Exception e) {
                    logger.warn("Could not retrieve pod logs: {}", e.getMessage());
                    return "Pod status: " + phase + " (logs unavailable)";
                }
            }
            
            return "Pod status: " + phase;
        } catch (Exception e) {
            String errorType = e.getClass().getName();
            String errorMsg = (e.getMessage() != null && !e.getMessage().isEmpty()) ? 
                               e.getMessage() : "No specific error message";
                               
            logger.error("Failed to check pod status. Error type: {} - Details: {}", errorType, errorMsg, e);
            return "Error checking pod status: " + errorType + " - " + errorMsg;
        }
    }
}