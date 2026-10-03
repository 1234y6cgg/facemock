package com.mockinterview.controller;
import com.mockinterview.service.model.*;
import com.mockinterview.infrastructure.model.CompatibleModelClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.util.List;
@RestController @RequestMapping("/api/settings/model")
public class ModelSettingsController {
    private final ModelSettingsService settings;private final CompatibleModelClient client;
    public ModelSettingsController(ModelSettingsService settings,CompatibleModelClient client){this.settings=settings;this.client=client;}
    private <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping public ResponseEntity<ModelSettingsService.View> get(){return noStore(settings.view());}
    @GetMapping("/presets") public List<ModelPresets.Preset> presets(){return ModelPresets.ALL;}
    @PutMapping public ResponseEntity<ModelSettingsService.View> save(@Valid @RequestBody ModelSettingsService.Update input){return noStore(settings.save(input));}
    @PostMapping("/test") public ResponseEntity<ModelSettingsService.Check> test(@Valid @RequestBody ModelSettingsService.Update input){return noStore(settings.check(input,client));}
    @DeleteMapping public ResponseEntity<ModelSettingsService.View> clear(@RequestParam long revision){return noStore(settings.clear(revision));}
}
