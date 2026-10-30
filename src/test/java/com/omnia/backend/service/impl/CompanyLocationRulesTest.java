package com.omnia.backend.service.impl;
import com.omnia.backend.dto.request.CompanyEditorRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanyLocationRulesTest {
    @Test void noPinAndValidPoints(){assertTrue(CompanyLocationRules.validPair(null,null));assertTrue(CompanyLocationRules.validPair(0.0,0.0));assertTrue(CompanyLocationRules.validPair(90.0,-180.0));}
    @Test void invalidOrPartialPoints(){assertFalse(CompanyLocationRules.validPair(41.0,null));assertFalse(CompanyLocationRules.validPair(null,19.0));assertFalse(CompanyLocationRules.validPair(Double.NaN,19.0));assertFalse(CompanyLocationRules.validPair(91.0,19.0));assertFalse(CompanyLocationRules.validPair(41.0,181.0));}
    @Test void legacyJsonDoesNotRequestLocationRemoval()throws Exception{CompanyEditorRequest r=new ObjectMapper().readValue("{}",CompanyEditorRequest.class);assertFalse(r.isLatitudeProvided());assertFalse(r.isLongitudeProvided());}
    @Test void explicitNullPairMeansClear()throws Exception{CompanyEditorRequest r=new ObjectMapper().readValue("{\"latitude\":null,\"longitude\":null}",CompanyEditorRequest.class);assertTrue(r.isLatitudeProvided());assertTrue(r.isLongitudeProvided());assertNull(r.getLatitude());assertNull(r.getLongitude());}
    @Test void presenceFlagsCannotBeForgedFromJson()throws Exception{CompanyEditorRequest r=new ObjectMapper().readValue("{\"latitude\":41,\"longitude\":19,\"latitudeProvided\":false,\"longitudeProvided\":false}",CompanyEditorRequest.class);assertTrue(r.isLatitudeProvided());assertTrue(r.isLongitudeProvided());}
}
