package de.ostms.lc.ai;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.LcDocument;
import java.io.IOException;
import java.util.*;

/**
 * Non-binding hints about differences between the documents of one presentation. They never feed the rule check and never decide anything:
 * every hint must quote its evidence from the documents, hints without verifiable evidence are dropped.
 */
public final class AiHintService {
 private static final ObjectMapper JSON=new ObjectMapper();
 public record Hint(String severity,String check,List<String> documents,String evidence,String explanation){}
 private static final int PER_DOCUMENT=2200,MAX_DOCUMENTS=10,MAX_HINTS=15;
 private final LlmClient client;
 public AiHintService(LlmClient client){this.client=client;}

 public List<Hint> hints(List<LcDocument> documents)throws IOException{
  var blocks=new ArrayList<String>();var corpus=new StringBuilder();
  for(var d:documents){
   if(blocks.size()>=MAX_DOCUMENTS)break;
   String text=d.getExtractedText()==null?"":d.getExtractedText().strip();if(text.isEmpty()||d.getDocumentType()==null)continue;
   String clipped=text.length()>PER_DOCUMENT?text.substring(0,PER_DOCUMENT):text;
   blocks.add("##### DOCUMENT: "+d.getDocumentType()+" #####\n"+clipped);corpus.append('\n').append(clipped);
  }
  if(blocks.size()<2)return List.of();
  return verify(client.generateJson(prompt(blocks)),AiFactSuggester.norm(corpus.toString()));
 }
 static String prompt(List<String> blocks){
  return "You help prepare a documentary-credit document examination. Below are documents of one presentation (OCR text, may contain recognition errors). Compare the documents with each other and report ONLY real or possible discrepancies in numbers, dates, quantities, weights, names, ports or vessels. "
   +"Ignore differences that only come from number formatting (decimal comma versus point), missing fields, or obviously garbled OCR text. Do not invent requirements of the credit. Each discrepancy needs a literal quote from one of the documents as evidence. "
   +"Reply with JSON only: {\"discrepancies\":[{\"severity\":\"HIGH|MEDIUM|LOW\",\"check\":short title,\"documents\":[document types],\"evidence\":literal quote,\"explanation\":one sentence}]}\n\n"+String.join("\n\n",blocks);
 }
 static List<Hint> verify(String answer,String normalizedCorpus){
  var out=new ArrayList<Hint>();
  JsonNode root;try{root=JSON.readTree(answer);}catch(tools.jackson.core.JacksonException invalid){return out;}
  for(JsonNode n:root.path("discrepancies")){
   if(out.size()>=MAX_HINTS)break;
   String evidence=n.path("evidence").asText(""),check=n.path("check").asText(""),why=n.path("explanation").asText("");
   String ne=AiFactSuggester.norm(evidence);
   if(check.isBlank()||ne.length()<6||!normalizedCorpus.contains(ne))continue;
   String sev=n.path("severity").asText("LOW").toUpperCase(Locale.ROOT);if(!Set.of("HIGH","MEDIUM","LOW").contains(sev))sev="LOW";
   var docs=new ArrayList<String>();n.path("documents").forEach(x->{if(x.isTextual()&&docs.size()<10)docs.add(x.asText());});
   out.add(new Hint(sev,cut(check,120),docs,cut(evidence.replaceAll("\\s+"," "),300),cut(why,300)));
  }
  return out;
 }
 private static String cut(String s,int max){return s.length()>max?s.substring(0,max)+"…":s;}
}
