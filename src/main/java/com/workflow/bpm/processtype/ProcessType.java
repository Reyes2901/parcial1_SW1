package com.workflow.bpm.processtype;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "process_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessType {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;
}
