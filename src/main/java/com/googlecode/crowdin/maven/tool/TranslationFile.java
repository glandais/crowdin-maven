package com.googlecode.crowdin.maven.tool;

import lombok.Value;

@Value
public class TranslationFile {
    String language;
    String mavenId;
    String name;
}
