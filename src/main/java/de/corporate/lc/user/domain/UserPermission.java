package de.corporate.lc.user.domain;
import java.util.*;
public enum UserPermission {
 LC_EDIT("LC-Akten bearbeiten"),LC_DELETE("LC-Akten löschen"),SWIFT_IMPORT("SWIFT-Nachrichten importieren"),DOCUMENT_UPLOAD("Dokumente hochladen"),DOCUMENT_DELETE("Dokumente löschen"),DOCUMENT_GENERATE("Dokumente erstellen"),DOCUMENT_REVIEW("Dokumente prüfen und freigeben"),TRAINING_MANAGE("Training bearbeiten"),USER_MANAGE("Benutzer verwalten"),AUDIT_VIEW("Audit-Protokoll ansehen"),SETTINGS_MANAGE("Firmen- und Dokumentvorlagen verwalten");
 private final String label;UserPermission(String label){this.label=label;}public String getLabel(){return label;}
 public static Set<UserPermission> defaults(UserRole role){if(role==UserRole.ADMIN)return EnumSet.allOf(UserPermission.class);if(role==UserRole.EDITOR||role==UserRole.USER)return EnumSet.of(LC_EDIT,SWIFT_IMPORT,DOCUMENT_UPLOAD,DOCUMENT_DELETE,DOCUMENT_GENERATE,DOCUMENT_REVIEW,TRAINING_MANAGE);return EnumSet.noneOf(UserPermission.class);}
}
