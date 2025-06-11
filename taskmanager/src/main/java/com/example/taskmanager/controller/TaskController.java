package com.example.taskmanager.controller;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskExecution;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    @Autowired
    private TaskRepository repository;

    @GetMapping
    public ResponseEntity<?> getTasks(@RequestParam(required = false) String id) {
        if (id != null) {
            return repository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(repository.findAll());
    }

    @PutMapping
    public ResponseEntity<?> createTask(@RequestBody Task task) {
        if (!isSafeCommand(task.getCommand())) {
            return ResponseEntity.badRequest().body("Unsafe command.");
        }
        return ResponseEntity.ok(repository.save(task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.ok("Deleted");
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTasks(@RequestParam String name) {
        List<Task> results = repository.findByNameContainingIgnoreCase(name);
        return results.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(results);
    }

    @PutMapping("/{id}/execute")
    public ResponseEntity<?> executeTask(@PathVariable String id) {
        Optional<Task> taskOpt = repository.findById(id);
        if (!taskOpt.isPresent()) return ResponseEntity.notFound().build();

        Task task = taskOpt.get();
        Instant start = Instant.now();
        String output = runCommand(task.getCommand());
        Instant end = Instant.now();

        TaskExecution exec = new TaskExecution();
        exec.setStartTime(start);
        exec.setEndTime(end);
        exec.setOutput(output);

        task.getTaskExecutions().add(exec);
        repository.save(task);
        return ResponseEntity.ok(exec);
    }

    private boolean isSafeCommand(String command) {
        return !command.matches(".*(rm|sudo|shutdown|reboot).*");
    }

    private String runCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                output.append(line).append("\n");
            return output.toString().trim();
        } catch (Exception e) {
            return "Command failed: " + e.getMessage();
        }
    }
}