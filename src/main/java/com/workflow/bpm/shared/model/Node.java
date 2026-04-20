package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    private String id;
    private String type;        // START, END, ACTIVITY, DECISION, FORK, JOIN
    private String label;
    private String laneId;
    private String assigneeRole;
    private Integer estimatedDurationHours;
    private FormSchema formSchema;
    
    @Builder.Default
    private Map<String, Object> position = new HashMap<>();  // {x, y} para el diagramador
    
    // Tipos de nodo como constantes
    public static final String TYPE_START = "START";
    public static final String TYPE_END = "END";
    public static final String TYPE_ACTIVITY = "ACTIVITY";
    public static final String TYPE_DECISION = "DECISION";
    public static final String TYPE_FORK = "FORK";
    public static final String TYPE_JOIN = "JOIN";
    
    // Métodos helper
    public boolean isStart() {
        return TYPE_START.equals(type);
    }
    
    public boolean isEnd() {
        return TYPE_END.equals(type);
    }
    
    public boolean isDecision() {
        return TYPE_DECISION.equals(type);
    }
    
    public boolean isFork() {
        return TYPE_FORK.equals(type);
    }
    
    public boolean isJoin() {
        return TYPE_JOIN.equals(type);
    }
    
    public boolean isActivity() {
        return TYPE_ACTIVITY.equals(type);
    }
}