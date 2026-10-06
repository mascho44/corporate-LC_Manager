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
    $('#avatarToggle').setAttribute('aria-label','Benutzermenü für '+(profile.displayName||profile.username));
    $('#avatarMenuName').textContent=profile.displayName||profile.username;$('#avatarMenuUsername').textContent=profile.username;
    setAvatar($('#headerAvatar'),profile);setAvatar($('#profileAvatar'),profile);
    $('#profileForm').elements.displayName.value=profile.displayName||'';
    $('#profileForm').elements.email.value=profile.email||'';$('#profileEmailNotice').classList.toggle('hidden',!!profile.email);$('#profileUsername').textContent=profile.username;$('#profileRole').textContent=({ADMIN:'Administrator',EDITOR:'Sachbearbeiter',VIEWER:'Leser',USER:'Sachbearbeiter'})[profile.roleName]||profile.roleName;
    $('#removeProfileAvatar').disabled=!profile.avatarUrl;
}
async function loadOwnProfile(){
    try{renderOwnProfile(await json('/api/profile'));$('#profileMessage').textContent='';}
    catch(error){$('#profileMessage').textContent='Profil konnte nicht geladen werden: '+error.message;}
}
function setupAvatarUi(){
    if($('#avatarToggle'))return;
    const wrapper=document.createElement('div');wrapper.className='avatar-control';
    wrapper.innerHTML='<button type="button" id="avatarToggle" class="avatar-toggle" aria-haspopup="menu" aria-expanded="false" aria-controls="avatarMenu" aria-label="Benutzermenü öffnen"><span id="headerAvatar" class="user-avatar"></span><span id="avatarUserName"></span><span aria-hidden="true">▾</span></button><div id="avatarMenu" class="avatar-menu hidden" role="menu" aria-label="Benutzerkonto"><div class="avatar-menu-identity"><b id="avatarMenuName"></b><small id="avatarMenuUsername"></small></div><button type="button" role="menuitem" id="avatarOpenProfile">Mein Profil</button><button type="button" role="menuitem" id="avatarOpenPassword">Passwort</button><button type="button" role="menuitem" id="avatarOpenTotp">Zwei-Faktor-Anmeldung</button><button type="button" role="menuitem" id="avatarLogout">Abmelden</button></div>';
    document.querySelector('header .header-actions').append(wrapper);
    const section=document.createElement('section');section.id='profileSection';section.className='panel hidden';
    section.innerHTML='<div class="panelhead"><div><h2>Mein Profil</h2><p>Persönliche Angaben und Profilbild verwalten.</p></div></div><div class="profile-layout"><div class="profile-picture-card"><div id="profileAvatar" class="user-avatar profile-avatar"></div><form id="profileAvatarForm"><label>Profilbild auswählen<input name="file" type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" required></label><small>PNG oder JPEG bis 5 MB. Das Bild wird mittig quadratisch zugeschnitten.</small><button type="submit">Profilbild hochladen</button></form><button type="button" id="removeProfileAvatar" class="secondary">Profilbild entfernen</button></div><div><p id="profileEmailNotice" role="status">Bitte hinterlegen Sie Ihre E-Mail-Adresse. Sie ist beim Speichern des Profils erforderlich.</p><form id="profileForm"><label>Anzeigename<input name="displayName" required maxlength="255" autocomplete="name"></label><label>E-Mail<input name="email" type="email" required maxlength="255" autocomplete="email"></label><dl class="profile-facts"><dt>Benutzername</dt><dd id="profileUsername"></dd><dt>Rolle</dt><dd id="profileRole"></dd></dl><button type="submit">Profil speichern</button></form><div class="profile-security"><h3>Anmeldung und Sicherheit</h3><button type="button" id="profilePassword" class="secondary">Passwort verwalten</button><button type="button" id="profileTotp" class="secondary">Zwei-Faktor-Anmeldung</button></div></div></div><div id="profileMessage" class="profile-message" role="status" aria-live="polite"></div>';
    $('#monitoringSection').before(section);
    setupLanguagePreferences(section);
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
    $('#profileForm').onsubmit=async event=>{event.preventDefault();const button=event.target.querySelector('button');button.disabled=true;try{renderOwnProfile(await json('/api/profile',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({displayName:event.target.elements.displayName.value,email:event.target.elements.email.value})}));$('#profileMessage').textContent='Profil gespeichert.';}catch(error){$('#profileMessage').textContent=error.message;}finally{button.disabled=false;}};
    $('#profileAvatarForm').onsubmit=async event=>{event.preventDefault();const form=event.target,file=form.elements.file.files[0],button=form.querySelector('button');if(!file||file.size>5*1024*1024){$('#profileMessage').textContent='Bitte ein PNG- oder JPEG-Bild bis 5 MB auswählen.';return;}button.disabled=true;try{const body=new FormData();body.append('file',file);renderOwnProfile(await json('/api/profile/avatar',{method:'POST',body}));form.reset();$('#profileMessage').textContent='Profilbild gespeichert.';}catch(error){$('#profileMessage').textContent=error.message;}finally{button.disabled=false;}};
    $('#removeProfileAvatar').onclick=async()=>{const button=$('#removeProfileAvatar');button.disabled=true;try{renderOwnProfile(await json('/api/profile/avatar',{method:'DELETE'}));$('#profileMessage').textContent='Profilbild entfernt.';}catch(error){$('#profileMessage').textContent=error.message;button.disabled=!ownProfile?.avatarUrl;}};
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
        try{await apply(await json('/api/profile/language',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({language:form.elements.language.value||null})}));form.querySelector('p').textContent=LcI18n.t('language.saved');}
        catch(error){form.querySelector('p').textContent=error.message;}finally{button.disabled=false;}
    };
}
