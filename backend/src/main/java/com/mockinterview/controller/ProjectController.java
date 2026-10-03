package com.mockinterview.controller;
import com.mockinterview.service.project.*;
import com.mockinterview.service.project.ProjectDtos.*;
import com.mockinterview.service.practice.PracticeDtos.RequestKey;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController @RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectStore store;private final ProjectPractice practice;
    public ProjectController(ProjectStore store,ProjectPractice practice){this.store=store;this.practice=practice;}
    @GetMapping public List<ProjectView> list(){return store.list();}
    @GetMapping("/resumes") public List<ResumeChoice> resumes(){return store.resumeChoices();}
    @PostMapping("/from-resume") public ProjectView fromResume(@Valid @RequestBody ImportResume body){return store.importResume(body);}
    @GetMapping("/{id}") public ProjectView get(@PathVariable String id){return store.get(id);}
    @PutMapping("/{id}/facts") public ProjectView facts(@PathVariable String id,@Valid @RequestBody SaveFacts body){return store.facts(id,body);}
    @PostMapping("/{id}/materials") public ProjectView material(@PathVariable String id,@Valid @RequestBody SaveMaterial body){return store.material(id,null,body);}
    @PutMapping("/{id}/materials/{key}") public ProjectView edit(@PathVariable String id,@PathVariable String key,@Valid @RequestBody SaveMaterial body){return store.material(id,key,body);}
    @DeleteMapping("/{id}/materials/{key}") public ProjectView deleteMaterial(@PathVariable String id,@PathVariable String key,@RequestParam int expectedRevision){return store.deleteMaterial(id,key,expectedRevision);}
    @PostMapping("/{id}/retry-index") public ProjectView retryIndex(@PathVariable String id){return store.retryIndex(id);}
    @PostMapping("/{id}/sessions") public SessionView start(@PathVariable String id,@Valid @RequestBody Start body){return practice.start(id,body);}
    @GetMapping("/{id}/sessions") public List<SessionView> history(@PathVariable String id){return practice.history(id);}
    @GetMapping("/sessions/{id}") public SessionView session(@PathVariable String id){return practice.get(id);}
    @PostMapping("/sessions/{id}/answers") public AttemptView answer(@PathVariable String id,@Valid @RequestBody Answer body){return practice.answer(id,body);}
    @PostMapping("/attempts/{id}/retry") public AttemptView retry(@PathVariable String id,@Valid @RequestBody RequestKey body){return practice.retry(id,body.clientRequestId());}
    @PostMapping("/attempts/{id}/followup") public SessionView followup(@PathVariable String id,@Valid @RequestBody RequestKey body){return practice.followup(id,body.clientRequestId());}
    @PostMapping("/sessions/{id}/complete") public SessionView complete(@PathVariable String id){return practice.complete(id);}
}
