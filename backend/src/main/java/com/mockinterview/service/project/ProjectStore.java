package com.mockinterview.service.project;
import com.mockinterview.domain.*;
import com.mockinterview.domain.project.*;
import com.mockinterview.repository.ResumeRepository;
import com.mockinterview.repository.project.*;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.practice.PracticeConflictException;
import com.mockinterview.controller.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static com.mockinterview.service.project.ProjectDtos.*;

@Service @Transactional
public class ProjectStore {
    private final TrainingProjectRepository projects;
    private final ProjectMaterialRepository materials;
    private final ProjectRevisionRepository revisions;
    private final ResumeRepository resumes;
    private final QuestionJson json;
    private final TransactionTemplate transaction;
    public ProjectStore(TrainingProjectRepository projects,ProjectMaterialRepository materials,ProjectRevisionRepository revisions,
        ResumeRepository resumes,QuestionJson json,PlatformTransactionManager manager){this.projects=projects;this.materials=materials;this.revisions=revisions;this.resumes=resumes;this.json=json;transaction=new TransactionTemplate(manager);}

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public synchronized ProjectView importResume(ImportResume request){return transaction.execute(status->{
        var old=projects.findByResumeIdAndResumeProjectIndex(request.resumeId(),request.projectIndex());
        if(old.isPresent()){if(old.get().isDeleted())throw new PracticeConflictException("此项目已归档，请从已有训练记录回看");return view(old.get());}
        var resume=resumes.findById(request.resumeId()).orElseThrow(()->new NotFoundException("简历不存在"));
        if(resume.getStatus()!=ResumeStatus.PARSED||resume.getParsedJson()==null)throw new PracticeConflictException("请等待简历解析完成");
        var parsed=json.read(resume.getParsedJson(),ResumeStructured.class);
        if(parsed.getProjects()==null||request.projectIndex()<0||request.projectIndex()>=parsed.getProjects().size())throw new IllegalArgumentException("项目序号不正确");
        var extracted=parsed.getProjects().get(request.projectIndex());var p=new TrainingProject();p.setId(UUID.randomUUID().toString());
        p.setResumeId(resume.getId());p.setResumeProjectIndex(request.projectIndex());p.setName(shortText(extracted.getName(),120,"未命名项目"));
        p.setExtractionJson(json.write(extracted));p.setFactsJson(json.write(new Facts(shortText(extracted.getDesc(),3000,""),
            shortText(join(extracted.getRole(),extracted.getResponsibilities()),3000,""),shortText(join("技术栈（待核对）：",extracted.getTechStack()),3000,""),
            shortText(join("简历提取（待核对）：",extracted.getHighlights()),3000,""),"","","",false)));
        p.setCreatedAt(Instant.now());p=projects.save(p);revise(p);return view(p);
    });}
    private String join(String prefix,List<String> items){return (prefix==null?"":prefix)+(items==null?"":"\n"+String.join("\n",items));}
    private String shortText(String value,int max,String fallback){return value==null||value.isBlank()?fallback:value.substring(0,Math.min(value.length(),max));}
    @Transactional(readOnly=true) public List<ResumeChoice> resumeChoices(){var result=new ArrayList<ResumeChoice>();for(var resume:resumes.findAll())if(resume.getStatus()==ResumeStatus.PARSED&&resume.getParsedJson()!=null){
        try{var parsed=json.read(resume.getParsedJson(),ResumeStructured.class);if(parsed.getProjects()!=null&&!parsed.getProjects().isEmpty())result.add(new ResumeChoice(resume.getId(),resume.getFilename(),parsed.getProjects().stream().map(p->p.getName()==null?"未命名项目":p.getName()).toList()));}
        catch(IllegalArgumentException ignored){} }return List.copyOf(result);}
    @Transactional(readOnly=true) public List<ProjectView> list(){return projects.findByDeletedFalseOrderByCreatedAtDesc().stream().map(this::view).toList();}
    @Transactional(readOnly=true) public ProjectView get(String id){return view(project(id));}
    public ProjectView facts(String id,SaveFacts body){var p=lock(id);expected(p,body.expectedRevision());p.setName(body.name().strip());p.setFactsJson(json.write(body.facts()));revise(p);return view(p);}
    public ProjectView material(String id,String key,SaveMaterial body){var p=lock(id);expected(p,body.expectedRevision());
        var all=latestMaterials(id);ProjectMaterial previous=null;
        if(key!=null){previous=all.get(key);if(previous==null||previous.isDeleted())throw new NotFoundException("项目材料不存在");}
        else if(all.values().stream().filter(m->!m.isDeleted()).count()>=20)throw new IllegalArgumentException("每项目最多二十份活动材料");
        int total=body.content().length();for(var m:all.values())if(!m.isDeleted()&&!m.getMaterialKey().equals(key))total+=m.getContent().length();
        if(total>50000)throw new IllegalArgumentException("项目补充材料总量不超过五万字");
        var m=new ProjectMaterial();m.setId(UUID.randomUUID().toString());m.setProjectId(id);m.setMaterialKey(key==null?UUID.randomUUID().toString():key);
        m.setVersion(previous==null?1:previous.getVersion()+1);m.setTitle(body.title().strip());m.setOrigin(body.origin().strip());m.setContent(body.content());m.setCreatedAt(Instant.now());materials.save(m);revise(p);return view(p);}
    public ProjectView deleteMaterial(String id,String key,int expected){var p=lock(id);expected(p,expected);var previous=latestMaterials(id).get(key);
        if(previous==null)throw new NotFoundException("项目材料不存在");if(previous.isDeleted())return view(p);
        var m=new ProjectMaterial();m.setId(UUID.randomUUID().toString());m.setProjectId(id);m.setMaterialKey(key);m.setVersion(previous.getVersion()+1);
        m.setTitle(previous.getTitle());m.setOrigin(previous.getOrigin());m.setContent("");m.setDeleted(true);m.setCreatedAt(Instant.now());materials.save(m);revise(p);return view(p);}
    public ProjectView retryIndex(String id){var p=lock(id);if(!"READY".equals(p.getIndexStatus()))p.setIndexStatus("PENDING");return view(p);}
    public void archive(String id,int expected){var p=lock(id);expected(p,expected);p.setDeleted(true);p.setIndexStatus("DELETE_PENDING");}
    private void revise(TrainingProject p){p.setRevision(p.getRevision()+1);p.setIndexStatus("PENDING");
        var snapshot=buildSnapshot(p);var r=new ProjectRevision();r.setId(UUID.randomUUID().toString());r.setProjectId(p.getId());r.setRevision(p.getRevision());r.setCreatedAt(Instant.now());r.setSnapshotJson(json.write(snapshot));revisions.save(r);}
    private Snapshot buildSnapshot(TrainingProject p){var facts=json.read(p.getFactsJson(),Facts.class);var sources=new ArrayList<Source>();
        for(var field:facts.fields().entrySet())if(!field.getValue().isBlank())sources.add(new Source("fact:"+field.getKey(),p.getRevision(),field.getKey(),
            "本人事实卡；简历 "+p.getResumeId()+" 项目 "+p.getResumeProjectIndex()+" 的提取结果由使用者核对",field.getValue(),field.getKey().equals("improvements")));
        for(var m:latestMaterials(p.getId()).values())if(!m.isDeleted())sources.add(new Source(m.getMaterialKey(),m.getVersion(),m.getTitle(),m.getOrigin(),m.getContent(),false));
        return new Snapshot(p.getId(),p.getName(),p.getRevision(),facts,List.copyOf(sources));}
    @Transactional(readOnly=true) public Snapshot snapshot(String id){var p=project(id);return snapshot(id,p.getRevision());}
    @Transactional(readOnly=true) public Snapshot snapshot(String id,int revision){return json.read(revisions.findByProjectIdAndRevision(id,revision).orElseThrow(()->new NotFoundException("项目版本不存在")).getSnapshotJson(),Snapshot.class);}
    @Transactional(readOnly=true) public List<String> pendingIndex(){return projects.findTop3ByIndexStatusInOrderByCreatedAtAsc(List.of("PENDING","DELETE_PENDING")).stream().map(TrainingProject::getId).toList();}
    public void indexFinished(String id,int revision,boolean success){var p=projects.lock(id).orElse(null);if(p==null||p.getRevision()!=revision)return;
        if(p.isDeleted()){p.setIndexStatus(success?"DELETED":"DELETE_FAILED");return;}
        p.setIndexStatus(success?"READY":"FAILED");if(success)p.setIndexedRevision(revision);}
    @Transactional(readOnly=true) public boolean archived(String id){return projects.findById(id).orElseThrow(()->new NotFoundException("项目不存在")).isDeleted();}
    @Transactional(readOnly=true) public int archivedRevision(String id){return projects.findById(id).orElseThrow(()->new NotFoundException("项目不存在")).getRevision();}
    private LinkedHashMap<String,ProjectMaterial> latestMaterials(String id){var latest=new LinkedHashMap<String,ProjectMaterial>();for(var m:materials.findByProjectIdOrderByCreatedAtAsc(id))
        latest.compute(m.getMaterialKey(),(key,old)->old==null||m.getVersion()>old.getVersion()?m:old);return latest;}
    private ProjectView view(TrainingProject p){return new ProjectView(p.getId(),p.getResumeId(),p.getResumeProjectIndex(),p.getName(),p.getRevision(),p.getIndexStatus(),p.getIndexedRevision(),
        json.read(p.getFactsJson(),Facts.class),p.getExtractionJson(),latestMaterials(p.getId()).values().stream().map(m->new MaterialView(m.getMaterialKey(),m.getVersion(),m.getTitle(),m.getOrigin(),m.getContent(),m.isDeleted(),m.getCreatedAt())).toList(),TEMPLATES);}
    private TrainingProject project(String id){var p=projects.findById(id).orElseThrow(()->new NotFoundException("项目不存在"));if(p.isDeleted())throw new PracticeConflictException("项目已归档，当前材料不再用于新练习");return p;}
    private TrainingProject lock(String id){var p=projects.lock(id).orElseThrow(()->new NotFoundException("项目不存在"));if(p.isDeleted())throw new PracticeConflictException("项目已归档");return p;}
    private void expected(TrainingProject p,int revision){if(p.getRevision()!=revision)throw new PracticeConflictException("材料已更新，请刷新后再保存，避免覆盖他人的修改");}
}
