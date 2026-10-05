package de.corporate.lc.check.api;

/** Version identifies both the implemented check and its documented limitations. */
public record RuleDefinition(String id,String version,String title,String basis,
                             String explanation,String limitations,String sourceUrl) {}
