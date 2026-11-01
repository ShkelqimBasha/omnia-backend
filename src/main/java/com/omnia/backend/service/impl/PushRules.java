package com.omnia.backend.service.impl;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
public final class PushRules {
 public static boolean quiet(boolean enabled,int start,int end,String zone,Instant now){if(!enabled)return false;int m=now.atZone(ZoneId.of(zone)).getHour()*60+now.atZone(ZoneId.of(zone)).getMinute();return start<end?m>=start&&m<end:m>=start||m<end;}
 public static void validateTime(int start,int end,String zone){if(start<0||start>1439||end<0||end>1439||start==end)throw new IllegalArgumentException("Invalid quiet hours");try{ZoneId.of(zone);}catch(Exception e){throw new IllegalArgumentException("Invalid time zone");}}
 static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean secretMatches(String secret,String hash){return MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII));}
 private PushRules(){}
}
