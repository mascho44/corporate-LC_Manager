package de.ostms.lc.sanctions;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Screens the parties of an LC file against the public EU, UN and OFAC lists. A support for the review, not a certified screening system. */
@Service
public class SanctionsService {
    private static final Logger log=LoggerFactory.getLogger(SanctionsService.class);
    public static final double MATCH=0.92, POSSIBLE=0.80;
    private static final int KEEP_VERSIONS=30;
    private static final long MAX_BYTES=150L*1024*1024;
    private static final String BIC="^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$";

    public record Hit(String party,String partyValue,SanctionsListParser.Entry entry,String listedName,double score){}
    public record SourceStatus(String source,LocalDateTime fetchedAt,int entries,String sha256,String published,String error){}
    public record Status(boolean fetchEnabled,boolean refreshing,List<SourceStatus> sources){}
    private record Indexed(SanctionsListParser.Entry entry,String name,List<String> tokens){}
    private record Loaded(String source,LocalDateTime fetchedAt,String sha256,String published,List<SanctionsListParser.Entry> entries){}

    private final SanctionsListRepository lists;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String,String> urls=new LinkedHashMap<>();
    private final boolean fetchEnabled;
    private volatile Map<String,Loaded> loaded=Map.of();
    private volatile Map<String,List<Indexed>> index=Map.of();
    private final Map<String,List<Hit>> cache=new ConcurrentHashMap<>();
    private final Map<String,String> errors=new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing=new AtomicBoolean();
    private volatile List<Indexed> vessels=List.of();
    private volatile Map<String,SanctionsListParser.Entry> vesselsByImo=Map.of();
    private volatile List<EmbargoCountries.Country> embargo=EmbargoCountries.list(null);
    @Value("${app.sanctions.embargo-extra:}") void setEmbargoExtra(String extra){embargo=EmbargoCountries.list(extra);}

    public SanctionsService(SanctionsListRepository lists,
                            @Value("${app.sanctions.fetch-enabled:true}") boolean fetchEnabled,
                            @Value("${app.sanctions.url.eu:https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw}") String eu,
                            @Value("${app.sanctions.url.un:https://scsanctions.un.org/resources/xml/en/consolidated.xml}") String un,
                            @Value("${app.sanctions.url.ofac-sdn:https://www.treasury.gov/ofac/downloads/sdn.csv}") String sdn,
                            @Value("${app.sanctions.url.ofac-alt:https://www.treasury.gov/ofac/downloads/alt.csv}") String alt){
        this.lists=lists;this.fetchEnabled=fetchEnabled;
        urls.put("EU",eu);urls.put("UN",un);urls.put("OFAC",sdn);urls.put("OFAC_ALT",alt);
    }

    // ---------- loading ----------

    @EventListener(ApplicationReadyEvent.class)
    public void loadStored(){
        background("sanctions-load",()->{try{reload();}catch(Exception e){log.warn("Sanktionslisten konnten nicht geladen werden: {}",e.getMessage());}});
    }

    static void background(String name,Runnable task){var t=new Thread(task,name);t.setDaemon(true);t.start();}

    /** First download some minutes after the start when nothing is stored yet; afterwards daily. */
    @Scheduled(initialDelayString="${app.sanctions.initial-delay-ms:600000}",fixedDelayString="${app.sanctions.check-interval-ms:3600000}")
    public void initialFetch(){if(fetchEnabled&&loaded.isEmpty())refresh();}
    @Scheduled(cron="${app.sanctions.cron:0 30 3 * * *}")
    public void dailyFetch(){if(fetchEnabled)refresh();}

    /** Downloads all lists, stores new versions and rebuilds the index. Returns false when a refresh is already running. */
    public boolean refresh(){
        if(!refreshing.compareAndSet(false,true))return false;
        try{
            for(String source:List.of("EU","UN","OFAC")){
                try{
                    byte[] content=download(urls.get(source));
                    if("OFAC".equals(source))content=bundle(content,download(urls.get("OFAC_ALT")));
                    store(source,content);errors.remove(source);
                }catch(Exception e){errors.put(source,e.getClass().getSimpleName()+": "+e.getMessage());log.warn("Sanktionsliste {} nicht aktualisiert: {}",source,e.getMessage());}
            }
            try{reload();}catch(Exception e){log.warn("Sanktionslisten konnten nicht neu geladen werden: {}",e.getMessage());}
            return true;
        }finally{refreshing.set(false);}
    }

    private byte[] download(String url)throws Exception{
        var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).header("User-Agent","Corporate-LC-Manager sanctions screening").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()!=200)throw new IOException("HTTP "+response.statusCode());
        try(var in=response.body()){
            byte[] data=in.readNBytes((int)MAX_BYTES+1);
            if(data.length>MAX_BYTES)throw new IOException("Datei zu groß");
            if(data.length<1000)throw new IOException("Datei unerwartet klein");
            return data;
        }
    }
    /** OFAC comes as two files; they are stored together, separated by a marker line. */
    static byte[] bundle(byte[] sdn,byte[] alt)throws IOException{
        var out=new ByteArrayOutputStream();out.write(sdn);out.write("\n#ALT#\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(alt);return out.toByteArray();
    }

    private void store(String source,byte[] content)throws Exception{
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        var latest=lists.findFirstBySourceOrderByFetchedAtDesc(source);
        if(latest.isPresent()&&sha.equals(latest.get().getSha256()))return;
        var parsed=parse(source,content);
        if(parsed.entries().size()<100)throw new IOException("nur "+parsed.entries().size()+" Einträge erkannt");
        var version=new SanctionsList();version.setSource(source);version.setSha256(sha);version.setEntryCount(parsed.entries().size());version.setPublished(parsed.published());version.setContent(gzip(content));
        lists.save(version);
        var all=lists.findAll().stream().filter(l->source.equals(l.getSource())).sorted(Comparator.comparing(SanctionsList::getFetchedAt).reversed()).toList();
        if(all.size()>KEEP_VERSIONS)lists.deleteAll(all.subList(KEEP_VERSIONS,all.size()));
    }

    static SanctionsListParser.Parsed parse(String source,byte[] content)throws Exception{
        return switch(source){
            case "EU"->SanctionsListParser.eu(new ByteArrayInputStream(content));
            case "UN"->SanctionsListParser.un(new ByteArrayInputStream(content));
            case "OFAC"->{
                String text=new String(content,java.nio.charset.StandardCharsets.UTF_8);int at=text.indexOf("\n#ALT#\n");
                byte[] sdn=(at<0?text:text.substring(0,at)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] alt=at<0?null:text.substring(at+7).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                yield SanctionsListParser.ofac(new ByteArrayInputStream(sdn),alt==null?null:new ByteArrayInputStream(alt));
            }
            default->throw new IllegalArgumentException(source);
        };
    }

    synchronized void reload()throws Exception{
        var next=new LinkedHashMap<String,Loaded>();
        for(String source:List.of("EU","UN","OFAC")){
            var latest=lists.findFirstBySourceOrderByFetchedAtDesc(source);if(latest.isEmpty())continue;
            var version=latest.get();
            var parsed=parse(source,gunzip(version.getContent()));
            next.put(source,new Loaded(source,version.getFetchedAt(),version.getSha256(),version.getPublished(),parsed.entries()));
        }
        use(next);
    }
    /** Builds the word index; also used by tests with in-memory entries. */
    void use(Map<String,Loaded> next){
        var idx=new HashMap<String,List<Indexed>>();
        next.values().forEach(l->l.entries().forEach(e->e.names().forEach(n->{
            var t=NameMatcher.tokens(n);if(!NameMatcher.distinctive(t))return;
            var item=new Indexed(e,n,t);
            new HashSet<>(t.stream().map(SanctionsService::key).toList()).forEach(k->idx.computeIfAbsent(k,x->new ArrayList<>()).add(item));
        })));
        var ships=new ArrayList<Indexed>();var imo=new HashMap<String,SanctionsListParser.Entry>();
        next.values().forEach(l->l.entries().stream().filter(e->e.kind()==SanctionsListParser.Kind.VESSEL).forEach(e->{
            e.names().forEach(n->{var t=NameMatcher.tokens(n);if(!t.isEmpty())ships.add(new Indexed(e,n,t));});
            e.imo().forEach(i->imo.put(i,e));
        }));
        vessels=ships;vesselsByImo=imo;
        index=idx;loaded=Collections.unmodifiableMap(new LinkedHashMap<>(next));cache.clear();
    }
    void useEntries(String source,List<SanctionsListParser.Entry> entries){var m=new LinkedHashMap<>(loaded);m.put(source,new Loaded(source,LocalDateTime.now(),"test","test",entries));use(m);}
    private static String key(String token){return token.substring(0,Math.min(3,token.length()));}

    // ---------- screening ----------

    /** Best hits for one name at or above the "possible" threshold, one per listed entry, highest first. */
    public List<Hit> match(String party,String value){
        String name=nameOf(value);var tokens=NameMatcher.tokens(name);
        if(!NameMatcher.distinctive(tokens))return List.of();
        String cacheKey=String.join(" ",tokens);
        var hits=cache.computeIfAbsent(cacheKey,k->{
            var best=new HashMap<SanctionsListParser.Entry,Hit>();var seen=new HashSet<Indexed>();
            for(String t:tokens)for(var candidate:index.getOrDefault(key(t),List.of())){
                if(!seen.add(candidate))continue;
                double s=NameMatcher.score(tokens,candidate.tokens());
                if(s>=POSSIBLE){var prev=best.get(candidate.entry());if(prev==null||s>prev.score())best.put(candidate.entry(),new Hit("","",candidate.entry(),candidate.name(),s));}
            }
            return best.values().stream().sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(3).toList();
        });
        if(cache.size()>20000)cache.clear();
        return hits.stream().map(h->new Hit(party,name,h.entry(),h.listedName(),h.score())).toList();
    }
    /** The name line of an MT field: the first line that is not an account ("/123…") or a BIC. */
    static String nameOf(String value){
        if(value==null)return "";
        for(String line:value.split("\\R")){String l=line.trim();if(l.isEmpty()||l.startsWith("/")||l.toUpperCase(Locale.ROOT).matches(BIC))continue;return l;}
        return "";
    }

    /** Findings for the review. Without loaded lists one neutral note says that no screening took place. */
    public List<CheckResult> screen(LetterOfCredit lc){return screen(lc,List.of());}
    public List<CheckResult> screen(LetterOfCredit lc,List<de.ostms.lc.document.domain.LcDocument> documents){
        var results=new ArrayList<CheckResult>();
        var lcFacts=facts(lc.getRuleFactsJson());
        embargoFindings(lc,lcFacts,documents,results);
        if(loaded.isEmpty()){results.add(new CheckResult(CheckResult.Severity.OK,"SANCTIONS_NOT_SCREENED","Sanktionsprüfung nicht durchgeführt: noch keine Sanktionslisten geladen."));return results;}
        int before=results.size();
        var parties=new LinkedHashMap<String,String>();
        parties.put("Auftraggeber",lc.getApplicant());parties.put("Begünstigter",lc.getBeneficiary());
        parties.put("Eröffnende Bank",lc.getIssuingBank());parties.put("Avisierende Bank",lc.getAdvisingBank());
        parties.put("Bestätigende Partei",lc.getConfirmationParty());parties.put("Remboursbank",lc.getReimbursingBank());
        parties.put("Bezogene Bank",lc.getDraweeBank());parties.put("Benutzbar bei",lc.getAvailableWith());
        parties.put("Empfänger (Consignee)",lcFacts.get("LC_CONSIGNEE"));parties.put("Notify Party",lcFacts.get("LC_NOTIFY_PARTY"));
        parties.forEach((party,value)->match(party,value).forEach(h->results.add(finding(h,null))));
        for(var doc:documents){
            var f=facts(doc.getRuleFactsJson());String name=doc.getOriginalFilename();
            Map.of("Empfänger (Consignee)","DOCUMENT_CONSIGNEE","Notify Party","DOCUMENT_NOTIFY_PARTY","Frachtführer","DOCUMENT_CARRIER").forEach((party,key)->match(party,f.get(key)).forEach(h->results.add(finding(h,name))));
            vesselFindings(f.get("DOCUMENT_VESSEL"),name,results);
        }
        if(results.size()==before)results.add(new CheckResult(CheckResult.Severity.OK,"SANCTIONS_SCREENED","Keine Treffer in den Sanktionslisten ("+String.join(", ",loaded.keySet())+")."));
        return results;
    }
    private CheckResult finding(Hit h,String documentName){
        boolean sure=h.score()>=MATCH;
        var e=h.entry();
        return new CheckResult(sure?CheckResult.Severity.DISCREPANCY:CheckResult.Severity.WARNING,sure?"SANCTIONS_MATCH":"SANCTIONS_POSSIBLE_MATCH",
            (sure?"Sanktionslisten-Treffer: ":"Möglicher Sanktionslisten-Treffer: ")+h.party()+" „"+h.partyValue()+"“ ähnelt „"+h.listedName()+"“ ("+e.source()+"). Vor weiterer Bearbeitung prüfen.",
            h.party()+": "+h.partyValue(),documentName,evidence(e,h.listedName(),String.format(Locale.GERMANY,"Übereinstimmung %.2f",h.score())));
    }
    private String evidence(SanctionsListParser.Entry e,String listedName,String how){
        var version=loaded.get(e.source());
        return listedName+" · "+e.source()+" "+(e.reference()==null?"":e.reference())+(e.programme()==null||e.programme().isBlank()?"":" ("+e.programme()+")")+" · "+how+" · Liste vom "+(version==null?"?":version.fetchedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }

    /** Vessel named in a transport document: by IMO number first, otherwise by name against listed vessels only. */
    void vesselFindings(String vessel,String documentName,List<CheckResult> results){
        if(vessel==null||vessel.isBlank())return;
        for(String imo:SanctionsListParser.imoNumbers(vessel)){
            var e=vesselsByImo.get(imo);
            if(e!=null){results.add(new CheckResult(CheckResult.Severity.DISCREPANCY,"SANCTIONS_VESSEL_MATCH","Gelistetes Schiff: IMO "+imo+" („"+e.name()+"“, "+e.source()+"). Vor weiterer Bearbeitung prüfen.","Schiff: "+vessel.trim(),documentName,evidence(e,e.name(),"IMO "+imo)));return;}
        }
        // Voyage numbers and the IMO note are not part of the name.
        String name=vessel.replaceAll("(?i)\\bIMO\\s*\\d{7}\\b","").replaceAll("(?i)\\b(V(OY(AGE)?)?\\.?\\s*(NO\\.?)?\\s*[A-Z0-9-]*\\d[A-Z0-9-]*)\\b","").replaceAll("(?i)\\b(M/?V|MS|MT|MV)\\b\\.?","");
        var tokens=NameMatcher.tokens(name);if(tokens.isEmpty()||String.join("",tokens).length()<4)return;
        Hit best=null;
        for(var v:vessels){double sc=NameMatcher.score(tokens,v.tokens());if(sc>=0.95&&(best==null||sc>best.score()))best=new Hit("Schiff",name.trim(),v.entry(),v.name(),sc);}
        if(best!=null)results.add(new CheckResult(CheckResult.Severity.WARNING,"SANCTIONS_VESSEL_POSSIBLE_MATCH","Möglicherweise gelistetes Schiff: „"+best.partyValue()+"“ ähnelt „"+best.listedName()+"“ ("+best.entry().source()+"). IMO-Nummer prüfen.","Schiff: "+vessel.trim(),documentName,evidence(best.entry(),best.listedName(),String.format(Locale.GERMANY,"Übereinstimmung %.2f",best.score()))));
    }

    private static final Map<String,String> LC_PLACES=new LinkedHashMap<>();
    private static final Map<String,String> DOC_PLACES=new LinkedHashMap<>();
    static{
        LC_PLACES.put("LC_ORIGIN_COUNTRY","Ursprungsland");LC_PLACES.put("LC_LOADING_PORT","Ladehafen");LC_PLACES.put("LC_DISCHARGE_PORT","Löschhafen");
        LC_PLACES.put("LC_DEPARTURE_AIRPORT","Abflughafen");LC_PLACES.put("LC_DESTINATION_AIRPORT","Zielflughafen");
        LC_PLACES.put("LC_APPLICANT_ADDRESS_COUNTRY","Land des Auftraggebers");LC_PLACES.put("LC_BENEFICIARY_ADDRESS_COUNTRY","Land des Begünstigten");
        LC_PLACES.put("LC_CONSIGNEE_ADDRESS_COUNTRY","Land des Empfängers");LC_PLACES.put("LC_NOTIFY_ADDRESS_COUNTRY","Land der Notify Party");
        DOC_PLACES.put("DOCUMENT_ORIGIN_COUNTRY","Ursprungsland");DOC_PLACES.put("DOCUMENT_LOADING_PORT","Ladehafen");DOC_PLACES.put("DOCUMENT_DISCHARGE_PORT","Löschhafen");
        DOC_PLACES.put("DOCUMENT_DEPARTURE_AIRPORT","Abflughafen");DOC_PLACES.put("DOCUMENT_DESTINATION_AIRPORT","Zielflughafen");
        DOC_PLACES.put("DOCUMENT_APPLICANT_ADDRESS_COUNTRY","Land des Auftraggebers");DOC_PLACES.put("DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY","Land des Begünstigten");
        DOC_PLACES.put("DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY","Land des Empfängers");DOC_PLACES.put("DOCUMENT_NOTIFY_ADDRESS_COUNTRY","Land der Notify Party");
    }
    /** Countries and places of the file and its documents that lie in an embargo country (independent of the downloaded lists). */
    void embargoFindings(LetterOfCredit lc,Map<String,String> lcFacts,List<de.ostms.lc.document.domain.LcDocument> documents,List<CheckResult> results){
        var seen=new HashSet<String>();
        java.util.function.BiConsumer<String[],String> check=(what,documentName)->EmbargoCountries.find(what[1],embargo).ifPresent(c->{
            if(seen.add(c.name()+"|"+what[0]+"|"+documentName))
                results.add(new CheckResult(CheckResult.Severity.WARNING,"EMBARGO_COUNTRY","Embargo-/Sanktionsland "+c.name()+": "+what[0]+" „"+what[1].trim().replaceAll("\\s+"," ")+"“. Genehmigungspflicht und Sanktionsrecht prüfen.",what[0]+": "+what[1].trim().replaceAll("\\s+"," "),documentName,"Länderliste Embargo/Sanktionen ("+c.name()+")"));
        });
        Map<String,String> parties=new LinkedHashMap<>();
        parties.put("Auftraggeber",lc.getApplicant());parties.put("Begünstigter",lc.getBeneficiary());parties.put("Eröffnende Bank",lc.getIssuingBank());parties.put("Avisierende Bank",lc.getAdvisingBank());
        parties.forEach((label,value)->{if(value!=null)check.accept(new String[]{label,value},null);});
        LC_PLACES.forEach((key,label)->{String v=lcFacts.get(key);if(v!=null)check.accept(new String[]{label,v},null);});
        for(var doc:documents){var f=facts(doc.getRuleFactsJson());DOC_PLACES.forEach((key,label)->{String v=f.get(key);if(v!=null)check.accept(new String[]{label,v},doc.getOriginalFilename());});}
    }
    /** Reviewed facts of the file or a document by field name; unreadable facts count as none. */
    static Map<String,String> facts(String json){
        try{var out=new HashMap<String,String>();de.ostms.lc.rulepack.RuleFacts.read(json).forEach((k,v)->out.put(k.name(),v));return out;}catch(RuntimeException e){return Map.of();}
    }

    public Status status(){
        var out=new ArrayList<SourceStatus>();
        for(String source:List.of("EU","UN","OFAC")){
            var l=loaded.get(source);
            out.add(l==null?new SourceStatus(source,null,0,null,null,errors.get(source)):new SourceStatus(source,l.fetchedAt(),l.entries().size(),l.sha256().substring(0,Math.min(12,l.sha256().length())),l.published(),errors.get(source)));
        }
        return new Status(fetchEnabled,refreshing.get(),out);
    }

    static byte[] gzip(byte[] data)throws IOException{var out=new ByteArrayOutputStream();try(var z=new GZIPOutputStream(out)){z.write(data);}return out.toByteArray();}
    static byte[] gunzip(byte[] data)throws IOException{try(var z=new GZIPInputStream(new ByteArrayInputStream(data))){return z.readAllBytes();}}
}
