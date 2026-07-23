package com.anish.url_shortener.common.util;

import com.github.f4b6a3.tsid.TsidCreator;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    public String generate() {
        return TsidCreator.getTsid().toLowerCase();
    }
}