package de.ostms.lc.rulepack;
import static de.ostms.lc.rulepack.PackDefinition.Field;
/** Explicit generic metadata pairs; no rule text or executable expressions. */
final class V5RuleCapabilities {
 private V5RuleCapabilities(){}
 static boolean pair(Field left,Field right){return switch(left){
  case DOCUMENT_APPLICANT_ADDRESS->right==Field.LC_APPLICANT_ADDRESS;
  case DOCUMENT_SHIPMENT_DATE->right==Field.LC_INSTALMENT_PERIOD_END_DATE;
  case DOCUMENT_PLACE_OF_RECEIPT->right==Field.LC_PLACE_OF_RECEIPT;
  case DOCUMENT_PLACE_OF_FINAL_DESTINATION->right==Field.LC_PLACE_OF_FINAL_DESTINATION;
  case DOCUMENT_PLACE_OF_SHIPMENT->right==Field.LC_PLACE_OF_SHIPMENT;
  case DOCUMENT_PLACE_OF_DESTINATION->right==Field.LC_PLACE_OF_DESTINATION;
  case DOCUMENT_INSURANCE_TYPE->right==Field.LC_INSURANCE_TYPE;
  case DOCUMENT_INCOTERM_SOURCE->right==Field.LC_INCOTERM_SOURCE;
  case DOCUMENT_DISCOUNT_INDICATED->right==Field.LC_DISCOUNT_REQUIRED;
  case DOCUMENT_AMOUNT->right==Field.LC_CLAIMED_AMOUNT;
  case DOCUMENT_CONSIGNEE->right==Field.PEER_CONSIGNEE;
  case DOCUMENT_FORM_TYPE->right==Field.LC_REQUIRED_FORM_TYPE;
  case DOCUMENT_CERTIFIED_STATEMENT->right==Field.LC_REQUIRED_STATEMENT;
  case DOCUMENT_PACKING_DESCRIPTION->right==Field.LC_PACKING_REQUIREMENT;
  case DOCUMENT_PRESENTATION_DATE->right==Field.LC_EXTENDED_EXPIRY_DATE;
  default->false;
 };}
}
