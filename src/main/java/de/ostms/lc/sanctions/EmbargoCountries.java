package de.ostms.lc.sanctions;

import java.util.*;

/** Countries and regions under comprehensive EU/US sanctions, with their usual spellings and main ports. A starting list; extend it with app.sanctions.embargo-extra (e.g. "Venezuela:VENEZUELA|CARACAS"). */
public final class EmbargoCountries {
    public record Country(String name,List<String> spellings){}
    private static final List<Country> DEFAULTS=List.of(
        new Country("Iran",List.of("IRAN","ISLAMIC REPUBLIC OF IRAN","BANDAR ABBAS","BANDAR IMAM KHOMEINI","BUSHEHR","CHABAHAR","ASALUYEH","TEHRAN","TEHERAN")),
        new Country("Nordkorea",List.of("NORTH KOREA","NORDKOREA","DPRK","DEMOCRATIC PEOPLES REPUBLIC OF KOREA","DEMOCRATIC PEOPLE S REPUBLIC OF KOREA","NAMPO","NAMPHO","CHONGJIN","RAJIN","WONSAN","PYONGYANG")),
        new Country("Syrien",List.of("SYRIA","SYRIEN","SYRIAN ARAB REPUBLIC","LATAKIA","LATTAKIA","TARTUS","TARTOUS","DAMASCUS","DAMASKUS")),
        new Country("Kuba",List.of("CUBA","KUBA","HAVANA","HAVANNA","LA HABANA","MARIEL","SANTIAGO DE CUBA")),
        new Country("Russland",List.of("RUSSIA","RUSSLAND","RUSSIAN FEDERATION","RUSSISCHE FOEDERATION","RUSSISCHE FODERATION","NOVOROSSIYSK","ST PETERSBURG","SAINT PETERSBURG","SANKT PETERSBURG","UST LUGA","PRIMORSK","VLADIVOSTOK","MURMANSK","MOSCOW","MOSKAU","KALININGRAD")),
        new Country("Belarus",List.of("BELARUS","WEISSRUSSLAND","MINSK")),
        new Country("Krim und besetzte Gebiete der Ukraine",List.of("CRIMEA","KRIM","SEVASTOPOL","SEWASTOPOL","KERCH","FEODOSIA","YALTA","DONETSK","LUHANSK","LUGANSK","ZAPORIZHZHIA","KHERSON","MARIUPOL","BERDYANSK"))
    );
    private EmbargoCountries(){}

    public static List<Country> list(String extra){
        var out=new ArrayList<>(DEFAULTS);
        if(extra!=null)for(String item:extra.split(";")){
            int at=item.indexOf(':');if(at<1)continue;
            var spellings=Arrays.stream(item.substring(at+1).split("\\|")).map(s->String.join(" ",tokensKeepingAll(s))).filter(s->!s.isBlank()).toList();
            if(!spellings.isEmpty())out.add(new Country(item.substring(0,at).trim(),spellings));
        }
        return out;
    }

    /** The embargo country a free text names (country field, port "HAMBURG, GERMANY", address line), as whole words only. */
    public static Optional<Country> find(String text,List<Country> countries){
        if(text==null||text.isBlank())return Optional.empty();
        String upper=text.trim().toUpperCase(Locale.ROOT);
        String words=" "+String.join(" ",tokensKeepingAll(text))+" ";
        for(var c:countries){
            for(String s:c.spellings())if(words.contains(" "+s+" "))return Optional.of(c);
        }
        // A bare ISO code only when the whole field is that code.
        var iso=Map.of("IR","Iran","KP","Nordkorea","SY","Syrien","CU","Kuba","RU","Russland","BY","Belarus");
        if(iso.containsKey(upper))return countries.stream().filter(c->c.name().equals(iso.get(upper))).findFirst();
        return Optional.empty();
    }
    private static List<String> tokensKeepingAll(String text){
        String v=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replace("ß","SS").replaceAll("[^A-Z0-9]+"," ").trim();
        return v.isEmpty()?List.of():List.of(v.split(" "));
    }
}
