package com.mockinterview.service.review;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static com.mockinterview.service.review.ReviewDtos.*;
import static org.junit.jupiter.api.Assertions.*;
class ReviewRulesTest {
    private final Preferences prefs=new Preferences("Asia/Shanghai",3,List.of(1,3,7),2,false,null,List.of());
    private Evidence e(int day,String status,boolean assisted,boolean review,String key){return new Evidence(key,key,key,"q",status,
        LocalDate.of(2026,10,1).plusDays(day).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),assisted,review,false,"原话");}
    private Point reduce(Evidence... evidence){return ReviewRules.reduce("shared.point","知识点","REDIS",List.of(evidence),prefs);}
    @Test void assistanceAndImmediateReanswerNeverConsolidate(){var p=reduce(e(0,"COVERED",true,false,"a"),e(0,"COVERED",false,true,"b"));assertEquals(0,p.independentPasses());assertEquals("TO_CONSOLIDATE",p.state());assertEquals(LocalDate.of(2026,10,2),p.dueDate());}
    @Test void twoSeparatedDueReviewsConsolidateAndUseOneThreeSevenDayIntervals(){var p=reduce(e(0,"COVERED",false,false,"a"),e(1,"COVERED",false,true,"b"),e(4,"COVERED",false,true,"c"));assertEquals(2,p.independentPasses());assertEquals("CONSOLIDATED",p.state());assertEquals(LocalDate.of(2026,10,12),p.dueDate());assertEquals(2,p.evidence().stream().filter(Evidence::passed).count());}
    @Test void earlyDuplicateAndSameDayReviewsDoNotCount(){var p=reduce(e(0,"COVERED",false,false,"a"),e(1,"COVERED",false,true,"b"),e(1,"COVERED",false,true,"c"),e(2,"COVERED",false,true,"d"));assertEquals(1,p.independentPasses());assertEquals(LocalDate.of(2026,10,5),p.dueDate());}
    @Test void uncertainIsNotEvidenceAndWrongReviewResetsIntervalAndStreak(){var p=reduce(e(0,"COVERED",false,false,"a"),e(1,"COVERED",false,true,"b"),e(4,"UNCERTAIN",false,true,"c"));assertEquals(2,p.validAnswers());assertEquals(1,p.independentPasses());var wrong=reduce(e(0,"COVERED",false,false,"a"),e(1,"COVERED",false,true,"b"),e(4,"INCORRECT",false,true,"c"));assertEquals("PRACTICING",wrong.state());assertEquals(0,wrong.independentPasses());assertEquals(LocalDate.of(2026,10,6),wrong.dueDate());}
    @Test void independentOrdinaryPracticeAndCrossQuestionFollowupsAreNotDelayedReviews(){var p=reduce(e(0,"COVERED",false,false,"a"),e(5,"COVERED",false,false,"b"));assertEquals(2,p.validAnswers());assertEquals(0,p.independentPasses());}
    @Test void dayBoundaryUsesConfiguredTimezoneAndDeletionDoesNotCreatePassWithoutBaseline(){var single=new Evidence("x","x","s","q","COVERED",Instant.parse("2026-10-01T16:00:00Z"),false,true,false,"");var p=reduce(single);assertEquals(LocalDate.of(2026,10,3),p.dueDate());assertEquals(0,p.independentPasses());assertEquals("UNPRACTICED",reduce().state());}
}
