package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    private String id;
    private String type;        // START, END, ACTIVITY, TASK, DECISION, FORK, JOIN
    private String label;
    private String laneId;
    private String assigneeRole;
    private Integer estimatedDurationHours;
    private FormSchema formSchema;

    /**
     * Only relevant on FORK nodes.
     * The engine stamps this value onto every TaskInstance created by
     * the branches that originate from this fork, so the JOIN can
     * detect when all siblings are done.
     */
    private String parallelGroupId;

    /**
     * Optional list of condition expressions for DECISION nodes
     * (redundant with Transition.condition but useful for UI editors).
     */
    @Builder.Default
    private List<String> conditions = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> position = new HashMap<>();  // {x, y} para el diagramador

    // ── Node type constants ──────────────────────────────────────────
    public static final String TYPE_START    = "START";
    public static final String TYPE_END      = "END";
    public static final String TYPE_ACTIVITY = "ACTIVITY";
    public static final String TYPE_TASK     = "TASK";      // alias for ACTIVITY
    public static final String TYPE_DECISION = "DECISION";
    public static final String TYPE_FORK     = "FORK";
    public static final String TYPE_JOIN     = "JOIN";

    // ── Helpers ─────────────────────────────────────────────────────
    public boolean isStart()    { return TYPE_START.equals(type); }
    public boolean isEnd()      { return TYPE_END.equals(type); }
    public boolean isDecision() { return TYPE_DECISION.equals(type); }
    public boolean isFork()     { return TYPE_FORK.equals(type); }
    public boolean isJoin()     { return TYPE_JOIN.equals(type); }
    public boolean isActivity() {
        return TYPE_ACTIVITY.equals(type) || TYPE_TASK.equals(type);
    }
}