/* Start der Anwendung: läuft nach allen app-Teilen (Funktionen aus allen Teilen müssen vorhanden sein). */
setupInboxUi();ensureTrainingUi();setupAppNavigation();setupWorkspacePresets();setupDocumentBatchUpload();ensureRoleUi();init().then(()=>examinationWorkspaceChannel?.postMessage({type:'WORKSPACE_READY',source:examinationWorkspaceTabId}));
