package com.workflow.bpm.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "test")
public class TestConnection {

    @Id
    private String id;
    private String message;

    public TestConnection() {}

    public TestConnection(String message) {
        this.message = message;
    }

    public String getId() { return id; }
    public String getMessage() { return message; }
}