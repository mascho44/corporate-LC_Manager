package de.corporate.lc.user.service;

import de.corporate.lc.user.api.ProfileView;
import de.corporate.lc.user.domain.AppUser;
import de.corporate.lc.user.domain.UserAvatar;
import de.corporate.lc.user.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class ProfileService {
    private final AppUserRepository users;
    private final UserAvatarRepository avatars;
    public ProfileService(AppUserRepository users, UserAvatarRepository avatars){this.users=users;this.avatars=avatars;}

    @Transactional(readOnly=true)
    public ProfileView profile(String username){return view(user(username));}

    @Transactional
    public ProfileView updateName(String username,String displayName,String email){
        if(displayName==null||displayName.isBlank()||displayName.trim().length()>255||displayName.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Bitte einen Anzeigenamen mit 1 bis 255 Zeichen eingeben.");
        String contact=UserEmail.required(email);AppUser user=user(username);user.setEmail(contact);user.setDisplayName(displayName.trim());users.save(user);return view(user);
    }

    @Transactional
    public ProfileView uploadAvatar(String username,MultipartFile file){
        AppUser user=user(username);
        if(file==null||file.isEmpty()||file.getSize()>5L*1024*1024)
            throw new IllegalArgumentException("Bitte ein PNG- oder JPEG-Bild bis 5 MB auswählen.");
        byte[] normalized;
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IllegalArgumentException("Das Bild ist ungültig. Erlaubt sind PNG und JPEG.");
            var reader=readers.next();
            try{
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!format.equals("png")&&!format.equals("jpeg")&&!format.equals("jpg"))
                    throw new IllegalArgumentException("Erlaubt sind PNG und JPEG.");
                reader.setInput(stream,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>8192||height>8192||(long)width*height>16_000_000)
                    throw new IllegalArgumentException("Das Bild ist zu groß. Maximal 16 Megapixel und 8192 Pixel je Seite.");
                BufferedImage original=reader.read(0);
                int crop=Math.min(width,height),size=Math.min(512,crop);
                BufferedImage avatar=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
                var graphics=avatar.createGraphics();
                try{graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int x=(width-crop)/2,y=(height-crop)/2;
                    graphics.drawImage(original,0,0,size,size,x,y,x+crop,y+crop,null);
                }finally{graphics.dispose();original.flush();}
                try(var output=new ByteArrayOutputStream()){ImageIO.write(avatar,"png",output);normalized=output.toByteArray();}
                finally{avatar.flush();}
            }finally{reader.dispose();}
        }catch(IOException ex){throw new IllegalArgumentException("Das Bild konnte nicht gelesen werden.",ex);}
        UserAvatar avatar=avatars.findById(user.getId()).orElseGet(UserAvatar::new);
        avatar.setUserId(user.getId());avatar.setContent(normalized);avatar.setUpdatedAt(Instant.now());avatars.save(avatar);
        return view(user);
    }

    @Transactional(readOnly=true)
    public byte[] avatar(String username){return avatars.findById(user(username).getId()).orElseThrow(()->new NoSuchElementException("Kein Profilbild vorhanden.")).getContent();}

    @Transactional
    public ProfileView deleteAvatar(String username){AppUser user=user(username);avatars.findById(user.getId()).ifPresent(avatars::delete);return new ProfileView(user.getUsername(),user.getDisplayName(),roleName(user),user.isTotpEnabled(),null,user.getEmail());}

    private AppUser user(String username){var user=users.findByUsernameIgnoreCase(username).orElseThrow(()->new NoSuchElementException("Benutzer nicht gefunden."));de.corporate.lc.tenant.domain.TenantContext.require(user.getTenantId());return user;}
    private String roleName(AppUser user){return user.getAssignedRole()==null?user.getRole().name():user.getAssignedRole().getName();}
    private ProfileView view(AppUser user){String url=avatars.findById(user.getId()).map(avatar->"/api/profile/avatar?v="+avatar.getUpdatedAt().toEpochMilli()).orElse(null);return new ProfileView(user.getUsername(),user.getDisplayName(),roleName(user),user.isTotpEnabled(),url,user.getEmail());}
}
