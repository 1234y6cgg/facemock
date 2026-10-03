package com.mockinterview.capability.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeDocument(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}") String id,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 20000) String content,
        @NotBlank @Size(max = 200) String source,
        @Size(max = 500) String sourceUrl,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags) {
}
