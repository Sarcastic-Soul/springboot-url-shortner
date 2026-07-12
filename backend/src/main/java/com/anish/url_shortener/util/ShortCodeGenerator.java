package com.anish.url_shortener.util;

import java.security.SecureRandom;

public class ShortCodeGenerator {

    private static final String CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length){

        StringBuilder sb = new StringBuilder();

        for(int i=0;i<length;i++){

            sb.append(
                    CHARS.charAt(
                            RANDOM.nextInt(CHARS.length())
                    )
            );

        }

        return sb.toString();
    }

}
