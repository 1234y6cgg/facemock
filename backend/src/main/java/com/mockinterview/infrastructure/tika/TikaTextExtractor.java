package com.mockinterview.infrastructure.tika;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaTextExtractor {

    private final Tika tika = new Tika();

    public String extract(InputStream in) throws IOException, TikaException {
        return tika.parseToString(in);
    }
}
