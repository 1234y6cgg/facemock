package com.mockinterview.service.resume;
import com.mockinterview.config.OcrProperties;
import com.mockinterview.infrastructure.ocr.PaddleOcrClient;
import com.mockinterview.infrastructure.tika.TikaTextExtractor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ResumeTextExtractorTest {
 private final PaddleOcrClient ocr=mock(PaddleOcrClient.class);
 private final OcrProperties properties=new OcrProperties();
 private final ResumeTextExtractor extractor=new ResumeTextExtractor(new TikaTextExtractor(),ocr,properties);
 private static final String TEXT="Java backend engineer building an order processing platform with Spring Boot MySQL Redis and reliable payment callbacks.";
 private byte[] pdf(boolean text,boolean scanned) throws Exception {
  try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
   if(text){var page=new PDPage();doc.addPage(page);try(var content=new PDPageContentStream(doc,page)){content.beginText();content.setFont(PDType1Font.HELVETICA,11);content.newLineAtOffset(20,750);content.showText(TEXT);content.endText();}}
   if(scanned){var page=new PDPage();doc.addPage(page);try(var content=new PDPageContentStream(doc,page)){content.drawImage(LosslessFactory.createFromImage(doc,new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB)),0,0,200,200);}}
   doc.save(out);return out.toByteArray();
  }
 }
 @Test void nativePdfWorksWithoutOcr() throws Exception {properties.setEnabled(false);var result=extractor.extract(pdf(true,false),"resume.pdf");assertThat(result.text()).contains("order processing");assertThat(result.method()).isEqualTo("TEXT");verifyNoInteractions(ocr);}
 @Test void mixedPdfRetainsBothPagesAndOcrsOnlyScannedPage() throws Exception {when(ocr.extract(any(),anyInt())).thenReturn(TEXT+" OCR page");var result=extractor.extract(pdf(true,true),"resume.pdf");assertThat(result.method()).isEqualTo("MIXED");assertThat(result.text()).contains("OCR page");verify(ocr,times(1)).extract(argThat(b->b.length>8&&b[1]==80),anyInt());}
 @Test void pngUsesOcr() throws Exception {try(var out=new ByteArrayOutputStream()){ImageIO.write(new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB),"png",out);when(ocr.extract(any(),anyInt())).thenReturn(TEXT);assertThat(extractor.extract(out.toByteArray(),"resume.PNG").method()).isEqualTo("OCR");}}
 @Test void failedScanDoesNotReturnPartialText() throws Exception {when(ocr.extract(any(),anyInt())).thenThrow(new ResumeExtractionException("OCR 暂不可用"));byte[] file=pdf(true,true);assertThatThrownBy(()->extractor.extract(file,"resume.pdf")).hasMessage("OCR 暂不可用");}
 @Test void enforcesPageLimit() throws Exception {properties.setMaxPdfPages(1);byte[] file=pdf(true,true);assertThatThrownBy(()->extractor.extract(file,"resume.pdf")).hasMessageContaining("最多支持 1 页");verifyNoInteractions(ocr);}
 @Test void rejectsEmptyOrDisguisedFiles(){assertThatThrownBy(()->extractor.validate(new byte[0],"resume.png")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->extractor.validate("plain text".getBytes(),"resume.pdf")).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectsBlankRecognition() throws Exception {when(ocr.extract(any(),anyInt())).thenReturn("   ");byte[] file=pdf(false,true);assertThatThrownBy(()->extractor.extract(file,"resume.pdf")).hasMessageContaining("未识别到足够");}
}
