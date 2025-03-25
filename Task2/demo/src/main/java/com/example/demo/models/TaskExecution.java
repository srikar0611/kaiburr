package com.example.demo.models;

import lombok.Data;
import java.time.Instant;

@Data
public class TaskExecution {
    private Instant startTime;
    private Instant endTime;
    private String output;

    public TaskExecution(Instant startTime, Instant endTime, String output) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.output = output;
    }
}
