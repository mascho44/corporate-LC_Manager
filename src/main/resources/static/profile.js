let ownProfile=null;
function profileInitials(name){return (name||'?').trim().split(/\s+/).slice(0,2).map(part=>Array.from(part)[0]||'').join('').toLocaleUpperCase('de-DE');}
function setAvatar(element,profile){
    element.replaceChildren();element.textContent=profileInitials(profile.displayName||profile.username);
    if(profile.avatarUrl){const image=document.createElement('img');image.src=profile.avatarUrl;image.alt='';image.onerror=()=>{image.remove();element.textContent=profileInitials(profile.displayName||profile.username);};element.textContent='';element.append(image);}
}
function closeAvatarMenu(focus=false){const menu=$('#avatarMenu'),toggle=$('#avatarToggle');menu?.classList.add('hidden');toggle?.setAttribute('aria-expanded','false');if(focus)toggle?.focus();}
function renderOwnProfile(profile){
    ownProfile=profile;currentUser.displayName=profile.displayName;currentUser.totpEnabled=profile.totpEnabled;
    $('#avatarUserName').textContent=profile.displayName||profile.username;
    $('#avatarToggle').setAttribute('aria-label',tx('prl.bd2b3c','Benutzermenü für ')+(profile.displayName||profile.username));
    $('#avatarMenuName').textContent=profile.displayName||profile.username;$('#avatarMenuUsername').textContent=profile.username;
    setAvatar($('#headerAvatar'),profile);setAvatar($('#profileAvatar'),profile);
    $('#profileForm').elements.displayName.value=profile.displayName||'';
    $('#profileForm').elements.email.value=profile.email||'';$('#profileEmailNotice').classList.toggle('hidden',!!profile.email);$('#profileUsername').textContent=profile.username;$('#profileRole').textContent=({ADMIN:'Administrator',EDITOR:'Sachbearbeiter',VIEWER:'Leser',USER:'Sachbearbeiter'})[profile.roleName]||profile.roleName;
    $('#removeProfileAvatar').disabled=!profile.avatarUrl;
}
async function loadOwnProfile(){
    try{renderOwnProfile(await json('/api/profile'));$('#profileMessage').textContent='';}
    catch(error){$('#profileMessage').textContent=tx('prl.ce1eed','Profil konnte nicht geladen werden: ')+error.message;}
}
function setupAvatarUi(){
    if($('#avatarToggle'))return;
    const wrapper=document.createElement('div');wrapper.className='avatar-control';
    wrapper.innerHTML='<button type="button" id="avatarToggle" class="avatar-toggle" aria-haspopup="menu" aria-expanded="false" aria-controls="avatarMenu" aria-label="'+tx('prh.4d0a72','Benutzermenü öffnen')+'"><span id="headerAvatar" class="user-avatar"></span><span id="avatarUserName"></span><span aria-hidden="true">▾</span></button><div id="avatarMenu" class="avatar-menu hidden" role="menu" aria-label="'+tx('prh.f4a9eb','Benutzerkonto')+'"><div class="avatar-menu-identity"><b id="avatarMenuName"></b><small id="avatarMenuUsername"></small></div><button type="button" role="menuitem" id="avatarOpenProfile">'+tx('prh.903821','Mein Profil')+'</button><button type="button" role="menuitem" id="avatarOpenPassword">'+tx('prh.3e45af','Passwort')+'</button><button type="button" role="menuitem" id="avatarOpenTotp">'+tx('prh.a441f7','Zwei-Faktor-Anmeldung')+'</button><button type="button" role="menuitem" id="avatarLogout">'+tx('prh.03b246','Abmelden')+'</button></div>';
    document.querySelector('header .header-actions').append(wrapper);
    const section=document.createElement('section');section.id='profileSection';section.className='panel hidden';
    section.innerHTML='<div class="panelhead"><div><h2>'+tx('prh.903821','Mein Profil')+'</h2><p>'+tx('prh.f05fcc','Persönliche Angaben und Profilbild verwalten.')+'</p></div></div><div class="profile-layout"><div class="profile-picture-card"><div id="profileAvatar" class="user-avatar profile-avatar"></div><form id="profileAvatarForm"><label>'+tx('prh.cb2aee','Profilbild auswählen')+'<input name="file" type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" required></label><small>'+tx('prh.b6f696','PNG oder JPEG bis 5 MB. Das Bild wird mittig quadratisch zugeschnitten.')+'</small><button type="submit">'+tx('prh.0c1a66','Profilbild hochladen')+'</button></form><button type="button" id="removeProfileAvatar" class="secondary">'+tx('prh.c94da7','Profilbild entfernen')+'</button></div><div><p id="profileEmailNotice" role="status">'+tx('prh.319173','Bitte hinterlegen Sie Ihre E-Mail-Adresse. Sie ist beim Speichern des Profils erforderlich.')+'</p><form id="profileForm"><label>'+tx('prh.2eb6eb','Anzeigename')+'<input name="displayName" required maxlength="255" autocomplete="name"></label><label>'+tx('prh.082e55','E-Mail')+'<input name="email" type="email" required maxlength="255" autocomplete="email"></label><dl class="profile-facts"><dt>'+tx('prh.cca849','Benutzername')+'</dt><dd id="profileUsername"></dd><dt>'+tx('prh.e897f3','Rolle')+'</dt><dd id="profileRole"></dd></dl><button type="submit">'+tx('prh.305502','Profil speichern')+'</button></form><div class="profile-security"><h3>'+tx('prh.612585','Anmeldung und Sicherheit')+'</h3><button type="button" id="profilePassword" class="secondary">'+tx('prh.a30c54','Passwort verwalten')+'</button><button type="button" id="profileTotp" class="secondary">'+tx('prh.a441f7','Zwei-Faktor-Anmeldung')+'</button></div></div></div><div id="profileMessage" class="profile-message" role="status" aria-live="polite"></div>';
    $('#monitoringSection').before(section);
    setupLanguagePreferences(section);
    setupNotificationPreferences(section);
    setupCalendarFeed(section);
    for(const id of ['appNavPassword','appNavTotp','appNavLogout','logoutBtn'])$('#'+id)?.classList.add('account-action-relocated');
    const toggle=$('#avatarToggle'),menu=$('#avatarMenu');
    const openMenu=()=>{menu.classList.remove('hidden');toggle.setAttribute('aria-expanded','true');};
    toggle.onclick=()=>menu.classList.contains('hidden')?openMenu():closeAvatarMenu();
    toggle.onkeydown=event=>{if(event.key==='Escape'){event.preventDefault();closeAvatarMenu(true);}else if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();openMenu();const buttons=[...menu.querySelectorAll('[role="menuitem"]')];buttons[event.key==='ArrowDown'?0:buttons.length-1].focus();}};
    menu.onkeydown=event=>{const buttons=[...menu.querySelectorAll('[role="menuitem"]')],index=buttons.indexOf(document.activeElement);if(event.key==='Escape'){event.preventDefault();closeAvatarMenu(true);}else if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){event.preventDefault();const next=event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowDown'?1:-1)+buttons.length)%buttons.length;buttons[next].focus();}};
    document.addEventListener('click',event=>{if(!wrapper.contains(event.target))closeAvatarMenu();});
    wrapper.addEventListener('focusout',event=>{if(!wrapper.contains(event.relatedTarget))closeAvatarMenu();});
    const securityPage=id=>{closeAvatarMenu();$('#'+id).click();};
    $('#avatarOpenProfile').onclick=()=>{closeAvatarMenu();appNavigate('profile',$('#avatarOpenProfile'));loadOwnProfile();};
    $('#avatarOpenPassword').onclick=$('#profilePassword').onclick=()=>securityPage('appNavPassword');
    $('#avatarOpenTotp').onclick=$('#profileTotp').onclick=()=>securityPage('appNavTotp');
    $('#avatarLogout').onclick=()=>{closeAvatarMenu();$('#logoutBtn').click();};
    $('#profileForm').onsubmit=async event=>{event.preventDefault();const button=event.target.querySelector('button');button.disabled=true;try{renderOwnProfile(await json('/api/profile',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({displayName:event.target.elements.displayName.value,email:event.target.elements.email.value})}));$('#profileMessage').textContent=tx('prl.4dae1e','Profil gespeichert.');}catch(error){$('#profileMessage').textContent=error.message;}finally{button.disabled=false;}};
    $('#profileAvatarForm').onsubmit=async event=>{event.preventDefault();const form=event.target,file=form.elements.file.files[0],button=form.querySelector('button');if(!file||file.size>5*1024*1024){$('#profileMessage').textContent=tx('prl.40572c','Bitte ein PNG- oder JPEG-Bild bis 5 MB auswählen.');return;}button.disabled=true;try{const body=new FormData();body.append('file',file);renderOwnProfile(await json('/api/profile/avatar',{method:'POST',body}));form.reset();$('#profileMessage').textContent=tx('prl.63f074','Profilbild gespeichert.');}catch(error){$('#profileMessage').textContent=error.message;}finally{button.disabled=false;}};
    $('#removeProfileAvatar').onclick=async()=>{const button=$('#removeProfileAvatar');button.disabled=true;try{renderOwnProfile(await json('/api/profile/avatar',{method:'DELETE'}));$('#profileMessage').textContent=tx('prl.ef7895','Profilbild entfernt.');}catch(error){$('#profileMessage').textContent=error.message;button.disabled=!ownProfile?.avatarUrl;}};
    auditLabels.USER_PROFILE_UPDATED='Eigenes Profil geändert';auditLabels.USER_AVATAR_UPDATED='Profilbild geändert';auditLabels.USER_AVATAR_DELETED='Profilbild entfernt';
    renderOwnProfile({username:currentUser.username,displayName:currentUser.displayName||currentUser.username,roleName:currentUser.role,totpEnabled:currentUser.totpEnabled,avatarUrl:null});
    loadOwnProfile();
}
function setupLanguagePreferences(section){
    if(!globalThis.LcI18n)return;
    const form=document.createElement('form');form.className='profile-language';
    form.innerHTML='<label><span data-i18n="language.title">Language</span><select name="language"><option value="" data-i18n="language.tenantDefault">Use tenant default</option><option value="en">English</option><option value="de">Deutsch</option></select></label><button type="submit" data-i18n="language.save">Save language</button><small data-i18n="language.note"></small><p role="status"></p>';
    section.querySelector('#profileForm').after(form);
    const apply=async preference=>{form.elements.language.value=preference.preferredLanguage||'';await LcI18n.setLanguage(preference.language);};
    json('/api/profile/language').then(apply).catch(error=>{form.querySelector('p').textContent=error.message;});
    form.onsubmit=async event=>{event.preventDefault();const button=form.querySelector('button');button.disabled=true;
        try{const before=LcI18n.language();await apply(await json('/api/profile/language',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({language:form.elements.language.value||null})}));form.querySelector('p').textContent=LcI18n.t('language.saved');if(LcI18n.language()!==before)setTimeout(()=>location.reload(),600);/* menus and pages built once must be rebuilt in the new language */}
        catch(error){form.querySelector('p').textContent=error.message;}finally{button.disabled=false;}
    };
}

function setupNotificationPreferences(section){
    const form=document.createElement('form');form.className='profile-language profile-notifications';
    form.innerHTML='<label><input type="checkbox" name="deadlineDigest"> <span></span></label><small></small><button type="submit"></button><p class="members-note" role="status"></p>';
    form.querySelector('span').textContent=tx('np.deadlineDigest','Tägliche Fristen-Zusammenfassung per E-Mail');
    form.querySelector('small').textContent=tx('np.deadlineDigestHint','Ihre zugewiesenen Akten: überfällige Fristen und die der nächsten 3 Tage sowie Dokumente, die auf Ihre Prüfung oder Freigabe warten, höchstens einmal täglich und nur, wenn etwas ansteht. Braucht eine E-Mail-Adresse im Profil.');
    form.querySelector('button').textContent=tx('np.save','Speichern');
    (section.querySelector('.profile-language')||section.querySelector('#profileForm')).after(form);
    const note=form.querySelector('p');
    json('/api/profile/notifications').then(preference=>{form.elements.deadlineDigest.checked=preference.deadlineDigest;}).catch(error=>{note.textContent=error.message;});
    form.onsubmit=async event=>{event.preventDefault();const button=form.querySelector('button');button.disabled=true;
        try{const preference=await json('/api/profile/notifications',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({deadlineDigest:form.elements.deadlineDigest.checked})});form.elements.deadlineDigest.checked=preference.deadlineDigest;note.textContent=tx('np.saved','Einstellung gespeichert.');}
        catch(error){form.elements.deadlineDigest.checked=false;note.textContent=error.message;}finally{button.disabled=false;}
    };
}

function setupCalendarFeed(section){
    const box=document.createElement('div');box.className='profile-language profile-calendar';
    box.innerHTML='<h3></h3><small class="cal-hint"></small><p class="members-note cal-state"></p><input class="cal-url" readonly hidden aria-label="URL"><small class="cal-warning" hidden></small><div class="cal-actions"><button type="button" class="cal-create"></button><button type="button" class="cal-copy secondary" hidden></button><button type="button" class="cal-revoke secondary" hidden></button></div><p class="members-note cal-note" role="status"></p>';
    box.querySelector('h3').textContent=tx('cal.title','Kalender-Abo');
    box.querySelector('.cal-hint').textContent=tx('cal.hint','Ihre Fristen und Aufgaben als Abonnement in Outlook, Apple Kalender oder Google Kalender. Der Kalender ruft den Link selbst ab, Änderungen erscheinen dort nach einiger Zeit.');
    box.querySelector('.cal-copy').textContent=tx('cal.copy','Link kopieren');
    box.querySelector('.cal-revoke').textContent=tx('cal.revoke','Abo beenden');
    const anchor=section.querySelector('.profile-notifications')||section.querySelector('#profileForm');anchor.after(box);
    const state=box.querySelector('.cal-state'),note=box.querySelector('.cal-note'),url=box.querySelector('.cal-url'),warning=box.querySelector('.cal-warning'),create=box.querySelector('.cal-create'),copy=box.querySelector('.cal-copy'),revoke=box.querySelector('.cal-revoke');
    const render=sub=>{
        state.textContent=sub.active?tx('cal.active','Abo aktiv (Link beginnt mit {p}…).').replace('{p}',sub.prefix||''):tx('cal.inactive','Kein Abo eingerichtet.');
        create.textContent=sub.active?tx('cal.renew','Neuen Link erzeugen'):tx('cal.create','Abo einrichten');
        revoke.hidden=!sub.active;
        if(sub.url){url.value=sub.url;url.hidden=false;copy.hidden=false;warning.hidden=false;warning.textContent=tx('cal.warning','Der Link wird nur jetzt angezeigt. Wer ihn kennt, sieht Ihre Fristen: nicht weitergeben. Ein neuer Link ersetzt den alten sofort.');}
        else{url.value='';url.hidden=true;copy.hidden=true;warning.hidden=true;}
    };
    json('/api/profile/calendar').then(render).catch(error=>{note.textContent=error.message;});
    create.onclick=async()=>{create.disabled=true;note.textContent='';try{render(await json('/api/profile/calendar',{method:'POST'}));}catch(error){note.textContent=error.message;}finally{create.disabled=false;}};
    revoke.onclick=async()=>{if(!confirm(tx('cal.revokeConfirm','Das Abo beenden? Der bisherige Link funktioniert dann nicht mehr.')))return;try{render(await json('/api/profile/calendar',{method:'DELETE'}));note.textContent=tx('cal.revoked','Abo beendet.');}catch(error){note.textContent=error.message;}};
    copy.onclick=async()=>{try{await navigator.clipboard.writeText(url.value);note.textContent=tx('cal.copied','Link kopiert.');}catch(error){url.select();note.textContent=tx('cal.copyManual','Bitte den markierten Link selbst kopieren.');}};
}
