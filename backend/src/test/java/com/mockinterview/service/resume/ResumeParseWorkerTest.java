package com.mockinterview.service.resume;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.domain.*;
import com.mockinterview.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ResumeParseWorkerTest {
 private final ResumeRepository repository=mock(ResumeRepository.class);private final ResumeParser parser=mock(ResumeParser.class);private final ResumeTextExtractor extractor=mock(ResumeTextExtractor.class);
 private final ResumeParseWorker worker=new ResumeParseWorker(repository,parser,new ObjectMapper(),extractor);
 private Resume resume(){var r=Resume.builder().id(1L).filename("resume.png").fileData(new byte[]{1}).status(ResumeStatus.PARSING).build();when(repository.findById(1L)).thenReturn(Optional.of(r));return r;}
 @Test void preservesOriginalAndOcrTextWhenModelFails(){var r=resume();when(extractor.extract(any(),anyString())).thenReturn(new ResumeTextExtractor.Result("recognized resume","OCR"));when(parser.parse(anyString())).thenThrow(new RuntimeException("secret response"));worker.parse(1L);assertThat(r.getStatus()).isEqualTo(ResumeStatus.FAILED);assertThat(r.getRawText()).isEqualTo("recognized resume");assertThat(r.getExtractionMethod()).isEqualTo("OCR");assertThat(r.getFileData()).containsExactly((byte)1);assertThat(r.getErrorMessage()).doesNotContain("secret").contains("模型配置");}
 @Test void ocrFailureNeverCallsModel(){var r=resume();when(extractor.extract(any(),anyString())).thenThrow(new ResumeExtractionException("OCR 识别超时"));worker.parse(1L);assertThat(r.getErrorMessage()).isEqualTo("OCR 识别超时");verifyNoInteractions(parser);}
 @Test void interruptedUploadsBecomeActionableFailures(){var r=resume();when(repository.findByStatus(ResumeStatus.PARSING)).thenReturn(List.of(r));worker.recoverInterrupted();assertThat(r.getStatus()).isEqualTo(ResumeStatus.FAILED);assertThat(r.getErrorMessage()).contains("原文件已保留");}
}
