package com.mockinterview.infrastructure.ocr;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.OcrProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class PaddleOcrClientTest {
 private HttpServer server;private final OcrProperties properties=new OcrProperties();private PaddleOcrClient client;
 @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("localhost",0),0);properties.setBaseUrl("http://localhost:"+server.getAddress().getPort());client=new PaddleOcrClient(properties,new ObjectMapper());}
 @AfterEach void close(){server.stop(0);}
 private void respond(String path,int code,String body){server.createContext(path,x->{x.getRequestBody().readAllBytes();byte[] b=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(code,b.length);x.getResponseBody().write(b);x.close();});server.start();}
 @Test void readsRecognizedText(){respond("/extract",200,"{\"text\":\"项目简历文字\"}");assertThat(client.extract(new byte[]{1},5)).isEqualTo("项目简历文字");}
 @Test void doesNotExposeServiceErrorBody(){respond("/extract",503,"{\"detail\":\"private data\"}");assertThatThrownBy(()->client.extract(new byte[]{1},5)).hasMessageContaining("暂时不可用").hasMessageNotContaining("private");}
 @Test void disabledServiceNeverConnects(){properties.setEnabled(false);assertThatThrownBy(()->client.extract(new byte[]{1},5)).hasMessageContaining("启用");assertThat(client.status().enabled()).isFalse();}
 @Test void rejectsInvalidSchema(){respond("/extract",200,"{\"text\":42}");assertThatThrownBy(()->client.extract(new byte[]{1},5)).hasMessageContaining("不完整");}
 @Test void healthChecksActualReadiness(){respond("/health",200,"{\"status\":\"UP\"}");assertThat(client.status().available()).isTrue();}
}
