package com.mockinterview.capability.speech;
import java.util.*;
import java.util.regex.Pattern;

/** Counts describe confirmed text, never accent, vocal quality, or knowledge mastery. Offsets are UTF-16. */
public record OralFeedback(long durationMs,int characterCount,List<String> suggestions,List<Occurrence> observations) {
    public record Occurrence(String word,int start,int end) {}
    public static OralFeedback describe(String text,long durationMs,int suggestedSeconds) {
        var occurrences=new ArrayList<Occurrence>();
        var matcher=Pattern.compile("嗯+|呃+|那个").matcher(text);
        while(matcher.find()) occurrences.add(new Occurrence(matcher.group(),matcher.start(),matcher.end()));
        var suggestions=new ArrayList<String>();
        if(durationMs>suggestedSeconds*1300L) suggestions.add("本次超过题目建议时长较多。下次先给结论，再保留一个关键场景和失败边界，避免重复展开。");
        else if(durationMs<Math.min(30000,suggestedSeconds*300L)) suggestions.add("本次回答较短。核对逐项反馈后，可用“机制—场景—边界”补充具体解释，无需为了时长凑字。");
        else suggestions.add("时长仅作练习参考。回放后核对是否先给结论，再用场景和边界组织解释。");
        if(!occurrences.isEmpty()) suggestions.add("下面列出确认文本中可能的口头词；它们也可能有正常语义，请结合原音核对，不直接扣分。");
        return new OralFeedback(durationMs,text.codePointCount(0,text.length()),List.copyOf(suggestions),List.copyOf(occurrences));
    }
}
