package de.ostms.lc.sanctions;

import java.text.Normalizer;
import java.util.*;

/** Fuzzy comparison of party names with listed names: word order, accents, punctuation and legal forms do not matter, small spelling differences are tolerated. */
public final class NameMatcher {
    private static final Set<String> NOISE=Set.of("LTD","LIMITED","GMBH","MBH","AG","SA","SAS","SARL","SRL","SPA","LLC","LLP","INC","CO","COMPANY","CORP","CORPORATION","PLC","BV","NV","KG","OHG","UG","JSC","OJSC","PJSC","CJSC","OOO","ZAO","OAO","PTE","PVT","FZE","FZCO","FZC","THE","AND","OF","FOR","DE","DER","DIE","UND","AS","AB","OY","KFT","SP","ZOO","SDN","BHD","EST","WLL");
    private NameMatcher(){}

    /** Words of a name after normalisation; legal forms, filler words and one-letter words are dropped. */
    public static List<String> tokens(String name){
        if(name==null)return List.of();
        String v=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT)
            .replace("ß","SS").replace("&"," AND ").replaceAll("[^A-Z0-9]+"," ").trim();
        if(v.isEmpty())return List.of();
        var out=new ArrayList<String>();
        for(String t:v.split(" "))if(t.length()>1&&!NOISE.contains(t))out.add(t);
        return out;
    }

    /** Similarity 0..1 of two names: fuzzy matched words weighted against the words of both names (Sørensen–Dice over word pairs). */
    public static double score(List<String> a,List<String> b){
        if(a.isEmpty()||b.isEmpty())return 0;
        var free=new ArrayList<>(b);double sum=0;
        for(String x:a){
            int best=-1;double bestScore=0;
            for(int i=0;i<free.size();i++){double s=jaroWinkler(x,free.get(i));if(s>bestScore){bestScore=s;best=i;}}
            if(best>=0&&bestScore>=0.88){sum+=bestScore;free.remove(best);}
        }
        return 2*sum/(a.size()+b.size());
    }

    /** A one-word name only counts when the word is long enough to be distinctive. */
    public static boolean distinctive(List<String> tokens){return tokens.size()>=2||(tokens.size()==1&&tokens.get(0).length()>=6);}

    static double jaroWinkler(String s,String t){
        if(s.equals(t))return 1;
        int ls=s.length(),lt=t.length();if(ls==0||lt==0)return 0;
        int window=Math.max(0,Math.max(ls,lt)/2-1);
        boolean[] ms=new boolean[ls],mt=new boolean[lt];int matches=0;
        for(int i=0;i<ls;i++){int from=Math.max(0,i-window),to=Math.min(lt,i+window+1);for(int j=from;j<to;j++)if(!mt[j]&&s.charAt(i)==t.charAt(j)){ms[i]=mt[j]=true;matches++;break;}}
        if(matches==0)return 0;
        int transpositions=0,k=0;
        for(int i=0;i<ls;i++)if(ms[i]){while(!mt[k])k++;if(s.charAt(i)!=t.charAt(k))transpositions++;k++;}
        double m=matches,jaro=(m/ls+m/lt+(m-transpositions/2.0)/m)/3;
        int prefix=0;while(prefix<Math.min(4,Math.min(ls,lt))&&s.charAt(prefix)==t.charAt(prefix))prefix++;
        return jaro+prefix*0.1*(1-jaro);
    }
}
