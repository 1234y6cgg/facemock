package com.mockinterview.service.review;
import java.time.*;
import java.util.*;
import static com.mockinterview.service.review.ReviewDtos.*;
public final class ReviewRules {
    private ReviewRules(){}
    public static Point reduce(String id,String title,String topic,List<Evidence> input,Preferences settings) {
        var ordered=input.stream().sorted(Comparator.comparing(Evidence::answeredAt).thenComparing(Evidence::attemptId)).toList();
        String state="UNPRACTICED",last=null,session=null; LocalDate due=null,lastPass=null;
        int count=0,passes=0,stage=0;
        var audited=new ArrayList<Evidence>();
        for(var e:ordered) {
            if("UNCERTAIN".equals(e.status())){audited.add(e);continue;}
            var day=e.answeredAt().atZone(ZoneId.of(settings.timezone())).toLocalDate();
            boolean eligible=e.delayed()&&!e.assisted()&&due!=null&&!day.isBefore(due)
                &&(lastPass==null||day.isAfter(lastPass));
            boolean pass=eligible&&"COVERED".equals(e.status());
            count++;last=e.status();session=e.sessionId();
            if(!"COVERED".equals(e.status())) {
                passes=0;stage=0;lastPass=null;state="PRACTICING";
                due=day.plusDays(settings.intervals().get(0));
            } else {
                if(pass){passes++;stage=Math.min(stage+1,2);lastPass=day;due=day.plusDays(settings.intervals().get(stage));}
                if(due==null) due=day.plusDays(settings.intervals().get(0));
                state=passes>=settings.requiredPasses()?"CONSOLIDATED":"TO_CONSOLIDATE";
            }
            audited.add(new Evidence(e.evaluationId(),e.attemptId(),e.sessionId(),e.questionId(),e.status(),e.answeredAt(),e.assisted(),e.delayed(),pass,e.quote()));
        }
        Collections.reverse(audited);
        return new Point(id,title,topic,state,due,last,count,passes,session,List.copyOf(audited.subList(0,Math.min(30,audited.size()))),
            (int)ordered.stream().filter(e->"UNCERTAIN".equals(e.status())).count());
    }
}
